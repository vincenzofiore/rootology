package io.github.vincenzofiore.rootology.pipeline;

/** A question as posed to every contestant, keyed by experiment case id (e.g. "easy-1"). */
public record Question(String id, String text) {
}
