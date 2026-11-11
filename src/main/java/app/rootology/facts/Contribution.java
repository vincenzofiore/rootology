package app.rootology.facts;

/**
 * A fact someone adds to the knowledge base (#24), on top of the curated files.
 * Ids are those of the knowledge base: crop ids and EPPO codes.
 */
public sealed interface Contribution {

    /** Another name for a crop or an organism, in Italian ("it") or English ("en"). */
    record Alias(String targetId, String name, String language) implements Contribution {
    }

    /** cropId is a companion or an antagonist of partnerId (declared in this direction only). */
    record Pair(String cropId, String partnerId, String relation, String effect) implements Contribution {
    }

    /** An organism affects a crop from one month to another (wrapping when from > to). */
    record Host(String organismId, String cropId, int fromMonth, int toMonth) implements Contribution {
    }
}
