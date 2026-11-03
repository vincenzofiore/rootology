package io.github.vincenzofiore.rootology.spaces;

/** A space as one member sees it: its id, its name, their role in it, and whether it is their personal space. */
public record SpaceSummary(long id, String name, Role role, boolean personal) {
}
