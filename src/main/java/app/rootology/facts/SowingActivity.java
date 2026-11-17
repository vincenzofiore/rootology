package app.rootology.facts;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The ways a crop goes into the ground, as the curated windows name them, each its own fact (#112): sowing in
 * a seedbed is not sowing in the open, and transplanting a seedling is not planting a bulb or a tuber, so a
 * calendar never joins them into one list of months.
 */
public enum SowingActivity {

    /** Sown where it grows. */
    SOW("sowIn"),
    /** Sown in a protected seedbed, to be transplanted later. */
    SOW_SEEDBED("seedbedIn"),
    /** A seedling moved to where it grows. */
    TRANSPLANT("transplantIn"),
    /** Planted from a bulb, a tuber, a clove or a young plant bought as such. */
    PLANT("plantIn");

    /** Every sowing and planting predicate of the facts. */
    public static final Set<String> PREDICATES = Arrays.stream(values()).map(SowingActivity::predicate)
            .collect(Collectors.toUnmodifiableSet());

    private final String predicate;

    SowingActivity(String predicate) {
        this.predicate = predicate;
    }

    /** The predicate of this activity's facts: (crop, predicate, MONTH). */
    public String predicate() {
        return predicate;
    }

    /** The activity a predicate stands for, empty for any other predicate (harvest included). */
    public static Optional<SowingActivity> ofPredicate(String predicate) {
        return Arrays.stream(values()).filter(a -> a.predicate.equals(predicate)).findFirst();
    }
}
