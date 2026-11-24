package app.rootology.access;

/** Where a flag stands (#165). Only an admin ever decides it; the system only raises it. */
public enum AccountFlagStatus {

    /** Waiting for an admin: the counts are there, nobody has decided yet. */
    OPEN,

    /** An admin blocked the account, from this flag or from the usage list. */
    BLOCKED,

    /** An admin chose to leave the account alone: it is never flagged again. */
    DISMISSED
}
