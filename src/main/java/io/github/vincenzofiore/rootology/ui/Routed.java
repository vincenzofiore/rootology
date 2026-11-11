package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.NotFoundException;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;

import java.util.Optional;

/**
 * The space and the garden a page works in, read from the page's own route (#56): the URL is the only
 * source of the context, so two browser tabs on two gardens never mix. A space the person is not in, or a
 * garden of another space, is "not found": the page never shows another space's data, and never a 403.
 */
final class Routed {

    /** The route parameter names, English like every URL. */
    static final String SPACE = "spaceId";
    static final String GARDEN = "gardenId";

    /** The route's space, and its garden (0 at the space level, where the rows without a garden live). */
    record Where(long spaceId, long gardenId) {
    }

    /** A page about a space only: the gardens overview, members, invitations, the space-level questions. */
    static Optional<Where> space(BeforeEnterEvent event, CurrentUser user) {
        Optional<Where> where = spaceOf(event, user);
        where.ifPresent(w -> user.switchTo(w.spaceId()));
        return where;
    }

    /**
     * A page about one garden when its route names one, otherwise the space level: ask, escalations and
     * history have both. A garden id that does not exist in the space is not found.
     */
    static Optional<Where> garden(BeforeEnterEvent event, CurrentUser user, GardenService gardens) {
        Optional<Where> space = spaceOf(event, user);
        if (space.isEmpty()) {
            return space;
        }
        Optional<String> named = event.getRouteParameters().get(GARDEN);
        if (named.isEmpty()) {
            user.switchTo(space.get().spaceId());
            return Optional.of(new Where(space.get().spaceId(), 0));
        }
        long gardenId = named.map(Routed::parse).orElse(-1L);
        Optional<GardenSummary> garden = gardenId <= 0 ? Optional.empty()
                : gardens.garden(space.get().spaceId(), user.login(), gardenId);
        if (garden.isEmpty()) {
            event.rerouteToError(NotFoundException.class);
            return Optional.empty();
        }
        user.switchTo(space.get().spaceId());
        return Optional.of(new Where(space.get().spaceId(), garden.get().id()));
    }

    private static Optional<Where> spaceOf(BeforeEnterEvent event, CurrentUser user) {
        long spaceId = number(event, SPACE);
        if (spaceId <= 0 || !user.memberOf(spaceId)) {
            event.rerouteToError(NotFoundException.class);
            return Optional.empty();
        }
        return Optional.of(new Where(spaceId, 0));
    }

    private static long number(BeforeEnterEvent event, String name) {
        return event.getRouteParameters().get(name).map(Routed::parse).orElse(-1L);
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Routed() {
    }
}
