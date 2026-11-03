package io.github.vincenzofiore.rootology.spaces;

/** A space as one member sees it: its id, its name and their role in it. */
public record SpaceSummary(long id, String name, Role role) {
}
