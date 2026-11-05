package io.github.vincenzofiore.rootology.pipeline;

import java.util.List;

/**
 * What a person submits for an escalated question: the answer values, an optional note,
 * and who answered (their GitHub login).
 */
public record HumanAnswer(List<String> answer, String note, String reviewer) {
}
