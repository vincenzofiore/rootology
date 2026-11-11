package app.rootology.access;

/** Where a request stands (#65). */
public enum AccessRequestStatus {

    /** Waiting for an admin. */
    PENDING,

    /** Admitted: the next sign-in creates the account, and then the request is gone. */
    APPROVED,

    /** Refused; the person may ask again after the configured waiting time. */
    REJECTED
}
