package io.github.vincenzofiore.rootology.spaces;

/** Someone tried to see or change a space they are not allowed to. */
public class SpaceAccessException extends RuntimeException {

    public SpaceAccessException(String message) {
        super(message);
    }
}
