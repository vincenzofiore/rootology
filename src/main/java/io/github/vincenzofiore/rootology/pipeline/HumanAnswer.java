package io.github.vincenzofiore.rootology.pipeline;

import java.util.List;

/** What a person submits for an escalated question: the answer values and an optional note. */
public record HumanAnswer(List<String> answer, String note) {
}
