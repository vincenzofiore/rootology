package app.rootology.ui;

import com.vaadin.flow.router.QueryParameters;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.history.RunHistory;
import app.rootology.pipeline.GardenPipeline;

import java.util.List;

/**
 * Where the links sent before #56 lead: the space's only garden when it has exactly one, otherwise the
 * space's gardens overview, or the space level when the old link is about rows without a garden (#56).
 * Never "the first garden", which would show a garden nobody chose.
 */
final class LegacyRoutes {

    static final String ASK = "ask";
    static final String GARDEN = "garden";
    static final String ESCALATIONS = "escalations";
    static final String HISTORY = "history";
    static final String KNOWLEDGE = "knowledge";
    static final String SPACE = "space";

    /**
     * The path an old link leads to; Vaadin keeps the query parameters of the forward, so {@code /ask?q=...}
     * still asks the question. The space is the one the old link named in {@code ?space=} when the person
     * belongs to it, otherwise the session's.
     */
    static String target(String path, QueryParameters query, GardenService gardens, RunHistory history,
                         GardenPipeline pipeline, CurrentUser user) {
        long spaceId = space(query, user);
        List<GardenSummary> all = gardens.gardens(spaceId, user.login());
        String base = "/spaces/" + spaceId;
        String only = all.size() == 1 ? String.valueOf(all.getFirst().id()) : null;
        return switch (path) {
            case ASK -> only == null ? base + "/ask" : base + "/gardens/" + only + "/ask";
            case GARDEN -> only == null ? base + "/gardens" : base + "/gardens/" + only + "/plots";
            case KNOWLEDGE -> only == null ? base + "/gardens" : base + "/gardens/" + only + "/knowledge";
            case ESCALATIONS -> questions(base, ESCALATIONS, only,
                    !pipeline.pending(spaceId, 0, user.login()).isEmpty());
            case HISTORY -> questions(base, HISTORY, only, !history.recent(spaceId, 0, user.login()).isEmpty());
            case SPACE -> base + "/members";
            default -> base + "/gardens";
        };
    }

    /**
     * The garden's page when the space has one garden; the space level when it has questions without a
     * garden (waiting, or stored before #56 in a space with several gardens); the overview otherwise.
     */
    private static String questions(String base, String kind, String only, boolean ungardened) {
        if (only != null) {
            return base + "/gardens/" + only + "/" + kind;
        }
        return ungardened ? base + "/" + kind : base + "/gardens";
    }

    private static long space(QueryParameters query, CurrentUser user) {
        return query.getSingleParameter("space").map(LegacyRoutes::parse)
                .filter(id -> id > 0 && user.memberOf(id))
                .orElseGet(() -> user.space().id());
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private LegacyRoutes() {
    }
}
