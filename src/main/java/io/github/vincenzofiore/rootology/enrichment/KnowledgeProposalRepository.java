package io.github.vincenzofiore.rootology.enrichment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface KnowledgeProposalRepository extends JpaRepository<KnowledgeProposal, Long> {

    List<KnowledgeProposal> findByStatusOrderById(ProposalStatus status);

    List<KnowledgeProposal> findByScopeAndStatusOrderById(ProposalScope scope, ProposalStatus status);

    List<KnowledgeProposal> findBySpaceIdOrderByIdDesc(long spaceId);
}
