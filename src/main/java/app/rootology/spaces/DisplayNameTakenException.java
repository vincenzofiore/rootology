package app.rootology.spaces;

/** Someone else already shows this display name (#106), as their chosen name or as their login. */
public class DisplayNameTakenException extends IllegalArgumentException {

    public DisplayNameTakenException(String name) {
        super("The display name is taken");
    }
}
