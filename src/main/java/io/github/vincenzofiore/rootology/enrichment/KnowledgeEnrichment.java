package io.github.vincenzofiore.rootology.enrichment;

import io.github.vincenzofiore.rootology.facts.Contribution;
import io.github.vincenzofiore.rootology.facts.KnowledgeContributions;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraphLoaded;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proposals, reviews and the facts they add to the knowledge graph. Members of a space propose; the
 * curators (GitHub logins in `rootology.knowledge.curators`) review the global ones.
 */
@Service
public class KnowledgeEnrichment {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEnrichment.class);

    private final KnowledgeProposalRepository proposals;
    private final KnowledgeContributions graph;
    private final SpaceService spaces;
    private final Set<String> curators;

    KnowledgeEnrichment(KnowledgeProposalRepository proposals, KnowledgeContributions graph, SpaceService spaces,
                        @Value("${rootology.knowledge.curators:}") String curators) {
        this.proposals = proposals;
        this.graph = graph;
        this.spaces = spaces;
        this.curators = Arrays.stream(curators.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(SpaceService::normalizeLogin).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isCurator(String login) {
        return login != null && curators.contains(SpaceService.normalizeLogin(login));
    }

    /**
     * Records a proposal from a member of the space. A space fact is checked and applied at once; a global
     * one needs a citation and waits for a curator. Throws {@link InvalidProposalException} with every
     * problem found.
     */
    @Transactional
    public ProposalSummary propose(long spaceId, String login, ProposalScope scope, Contribution contribution,
                                   String citation) {
        requireMember(spaceId, login);
        String cited = citation == null || citation.isBlank() ? null : citation.trim();
        List<String> problems = new java.util.ArrayList<>(
                graph.problems(contribution, scope == ProposalScope.SPACE ? spaceId : null));
        if (scope == ProposalScope.GLOBAL && cited == null) {
            problems.add("a fact for everyone needs a cited source");
        }
        if (cited != null && cited.length() > 500) {
            problems.add("a citation must have at most 500 characters");
        }
        if (!problems.isEmpty()) {
            throw new InvalidProposalException(problems);
        }
        KnowledgeProposal proposal = proposals.saveAndFlush(new KnowledgeProposal(spaceId, scope, contribution, cited,
                SpaceService.normalizeLogin(login), Instant.now()));
        if (proposal.getStatus() == ProposalStatus.APPROVED) {
            graph.apply(proposal.getId(), proposal.visibleTo(), contribution);
        }
        return proposal.summary();
    }

    /** The proposals made from this space, newest first: pending, approved and rejected. */
    @Transactional(readOnly = true)
    public List<ProposalSummary> ofSpace(long spaceId, String login) {
        requireMember(spaceId, login);
        return proposals.findBySpaceIdOrderByIdDesc(spaceId).stream().map(KnowledgeProposal::summary).toList();
    }

    /**
     * The approved proposal behind a source id `proposal-<id>`, if this member of this space may see it:
     * a global fact, or a fact of this space.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<ProposalSummary> source(long spaceId, String login, String sourceId) {
        if (sourceId == null || !sourceId.matches("proposal-\\d{1,18}") || !spaces.isMember(spaceId, login)) {
            return java.util.Optional.empty();
        }
        return proposals.findById(Long.parseLong(sourceId.substring("proposal-".length())))
                .filter(p -> p.getStatus() == ProposalStatus.APPROVED)
                .filter(p -> p.getScope() == ProposalScope.GLOBAL || p.getSpaceId() == spaceId)
                .map(KnowledgeProposal::summary);
    }

    /** The global proposals waiting for a curator, oldest first. */
    @Transactional(readOnly = true)
    public List<ProposalSummary> pending(String login) {
        requireCurator(login);
        return proposals.findByStatusOrderById(ProposalStatus.PENDING).stream().map(KnowledgeProposal::summary)
                .toList();
    }

    /** Approves a global proposal, checking it again against today's knowledge, and adds it for everyone. */
    @Transactional
    public ProposalSummary approve(String login, long proposalId, String note) {
        KnowledgeProposal proposal = pendingProposal(login, proposalId);
        List<String> problems = graph.problems(proposal.contribution(), null);
        if (!problems.isEmpty()) {
            throw new InvalidProposalException(problems);
        }
        proposal.review(ProposalStatus.APPROVED, SpaceService.normalizeLogin(login), blankToNull(note), Instant.now());
        graph.apply(proposal.getId(), null, proposal.contribution());
        return proposal.summary();
    }

    @Transactional
    public ProposalSummary reject(String login, long proposalId, String note) {
        KnowledgeProposal proposal = pendingProposal(login, proposalId);
        proposal.review(ProposalStatus.REJECTED, SpaceService.normalizeLogin(login), blankToNull(note), Instant.now());
        return proposal.summary();
    }

    /** The graph was rebuilt from the curated files: write the approved facts again, in the order they came. */
    @EventListener
    @Transactional(readOnly = true)
    void reapply(KnowledgeGraphLoaded loaded) {
        int applied = 0;
        for (ProposalScope scope : ProposalScope.values()) {
            for (KnowledgeProposal p : proposals.findByScopeAndStatusOrderById(scope, ProposalStatus.APPROVED)) {
                try {
                    graph.apply(p.getId(), p.visibleTo(), p.contribution());
                    applied++;
                } catch (RuntimeException e) {
                    // A curated file may have changed under an old proposal; keep going and say so.
                    log.warn("Could not reapply knowledge proposal {}: {}", p.getId(), e.getMessage());
                }
            }
        }
        log.info("Reapplied {} contributed facts", applied);
    }

    private KnowledgeProposal pendingProposal(String login, long proposalId) {
        requireCurator(login);
        KnowledgeProposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("No proposal " + proposalId));
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new IllegalStateException("Proposal " + proposalId + " was already reviewed");
        }
        return proposal;
    }

    private void requireMember(long spaceId, String login) {
        if (!spaces.isMember(spaceId, login)) {
            throw new SpaceAccessException(login + " is not a member of space " + spaceId);
        }
    }

    private void requireCurator(String login) {
        if (!isCurator(login)) {
            throw new SpaceAccessException(login + " is not a knowledge curator");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
