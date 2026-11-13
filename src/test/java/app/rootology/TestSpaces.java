package app.rootology;

import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;

/**
 * The personal space of a login in tests (#98). The test containers are shared, so a login may already belong
 * to spaces other test classes made (often as a guest): its first space is not always its own.
 */
public final class TestSpaces {

    public static long personal(SpaceService spaces, String login) {
        return spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
    }

    private TestSpaces() {
    }
}
