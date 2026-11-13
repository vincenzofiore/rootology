package app.rootology.spaces;

/**
 * What the invitation page shows before the person accepts: the space, or why it is refused. forEmail is the
 * masked address the invitation is bound to, or null.
 */
public record InvitationPreview(String spaceName, Role role, String refusal, String forEmail) {

    public boolean acceptable() {
        return refusal == null;
    }
}
