package app.rootology.facts;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How deep a crop's soil must be in a container (#186), as the curated `container-depths.csv` gives it: three
 * classes, each with the least soil depth it asks for in centimetres. The classes come from the source's
 * values (see the issue); a crop the source does not cover has no class, and the rule then says it does not
 * know rather than guessing one.
 */
public enum DepthClass {

    /** The shallowest container the crop grows in. */
    SHALLOW(15),
    MEDIUM(25),
    DEEP(30);

    private final int minimumCm;

    DepthClass(int minimumCm) {
        this.minimumCm = minimumCm;
    }

    /** The least soil depth, in centimetres, that satisfies this class. */
    public int minimumCm() {
        return minimumCm;
    }

    /** The names the curated file may use, for the validator. */
    public static Set<String> names() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    /** The class a curated row names, empty when the name is not one of ours. */
    public static Optional<DepthClass> parse(String name) {
        String clean = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        for (DepthClass value : values()) {
            if (value.name().equals(clean)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
