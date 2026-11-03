package io.github.vincenzofiore.rootology.spaces;

/** What the invitation page shows before the person accepts: the space, or why it is refused. */
public record InvitationPreview(String spaceName, Role role, String refusal) {

    public boolean acceptable() {
        return refusal == null;
    }
}
