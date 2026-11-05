package io.github.vincenzofiore.rootology.enrichment;

import io.github.vincenzofiore.rootology.facts.Contribution;

import java.time.Instant;

/** A proposal as the UI sees it, with its review if it had one. */
public record ProposalSummary(long id, Long spaceId, ProposalScope scope, Contribution contribution, String citation,
                              String proposedBy, Instant createdAt, ProposalStatus status, String reviewedBy,
                              Instant reviewedAt, String reviewNote, String withdrawnBy) {
}
