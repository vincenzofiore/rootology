package io.github.vincenzofiore.rootology.enrichment;

import io.github.vincenzofiore.rootology.facts.Contribution;
import io.github.vincenzofiore.rootology.facts.Contribution.Alias;
import io.github.vincenzofiore.rootology.facts.Contribution.Host;
import io.github.vincenzofiore.rootology.facts.Contribution.Pair;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A proposed fact and its review. The fact's fields depend on its kind; the others stay null. */
@Entity
@Table(name = "knowledge_proposal")
class KnowledgeProposal {

    enum Kind { ALIAS, PAIR, HOST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for an approved global fact whose space was deleted with an account (#40). */
    private Long spaceId;

    @Enumerated(EnumType.STRING)
    private ProposalScope scope;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    private String targetId;
    private String aliasName;
    private String aliasLanguage;
    private String cropId;
    private String partnerId;
    private String relation;
    private String effect;
    private String organismId;
    private Integer fromMonth;
    private Integer toMonth;

    private String citation;
    private String proposedBy;
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private ProposalStatus status;

    private String reviewedBy;
    private Instant reviewedAt;
    private String reviewNote;
    private String withdrawnBy;
    private Instant withdrawnAt;

    protected KnowledgeProposal() {
    }

    KnowledgeProposal(long spaceId, ProposalScope scope, Contribution contribution, String citation,
                      String proposedBy, Instant createdAt) {
        this.spaceId = spaceId;
        this.scope = scope;
        this.citation = citation;
        this.proposedBy = proposedBy;
        this.createdAt = createdAt;
        // A space fact needs no review: it only changes what its own space sees.
        this.status = scope == ProposalScope.SPACE ? ProposalStatus.APPROVED : ProposalStatus.PENDING;
        switch (contribution) {
            case Alias a -> {
                kind = Kind.ALIAS;
                targetId = a.targetId();
                aliasName = a.name().trim();
                aliasLanguage = a.language();
            }
            case Pair p -> {
                kind = Kind.PAIR;
                cropId = p.cropId();
                partnerId = p.partnerId();
                relation = p.relation();
                effect = p.effect().trim();
            }
            case Host h -> {
                kind = Kind.HOST;
                organismId = h.organismId();
                cropId = h.cropId();
                fromMonth = h.fromMonth();
                toMonth = h.toMonth();
            }
        }
    }

    Contribution contribution() {
        return switch (kind) {
            case ALIAS -> new Alias(targetId, aliasName, aliasLanguage);
            case PAIR -> new Pair(cropId, partnerId, relation, effect);
            case HOST -> new Host(organismId, cropId, fromMonth, toMonth);
        };
    }

    /** The space whose answers see this fact, or null when everyone does. */
    Long visibleTo() {
        return scope == ProposalScope.SPACE ? spaceId : null;
    }

    void review(ProposalStatus outcome, String curator, String note, Instant now) {
        this.status = outcome;
        this.reviewedBy = curator;
        this.reviewNote = note;
        this.reviewedAt = now;
    }

    /** A space fact, or a global proposal not yet reviewed; an approved global fact belongs to the curators. */
    boolean canBeWithdrawn() {
        return status == ProposalStatus.PENDING || (status == ProposalStatus.APPROVED && scope == ProposalScope.SPACE);
    }

    void withdraw(String login, Instant now) {
        this.status = ProposalStatus.WITHDRAWN;
        this.withdrawnBy = login;
        this.withdrawnAt = now;
    }

    ProposalSummary summary() {
        return new ProposalSummary(id, spaceId, scope, contribution(), citation, proposedBy, createdAt, status,
                reviewedBy, reviewedAt, reviewNote, withdrawnBy);
    }

    Long getId() { return id; }
    Long getSpaceId() { return spaceId; }
    ProposalScope getScope() { return scope; }
    ProposalStatus getStatus() { return status; }
}
