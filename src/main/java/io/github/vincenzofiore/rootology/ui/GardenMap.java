package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.shared.Registration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The gardens of a space on a map (#66): a thin Java side over the web component in
 * {@code frontend/garden-map.ts}, which draws MapLibre GL JS on the OpenFreeMap tiles. The component only
 * displays what this page computed, and reports back, each checked here before it is let through: a marker
 * was chosen, a popup's Open, Edit or Delete button was pressed, or a point on empty ground was chosen to add
 * a garden there. The popup carries every action of the table's row, so the map alone is enough.
 */
@Tag("rootology-garden-map")
@JsModule("./garden-map.ts")
@NpmPackage(value = "maplibre-gl", version = GardenMap.VERSION)
public final class GardenMap extends Component {

    /** Maplibre GL JS: BSD-3-Clause, the newest stable released more than two weeks before #66 was built. */
    public static final String VERSION = "6.16.0";

    /**
     * One garden in a popup, every line already worded in the page's language. {@code altitude} is the value
     * alone ("120 m"), {@code crops} and {@code waiting} the counts of crops growing today and questions waiting:
     * the popup shows each beside an icon, and the word is the icon's tooltip.
     */
    public record Pin(String id, String name, String town, String altitude, int crops, int waiting) {
    }

    /**
     * The gardens that share one rounded position: one marker, one popup listing them all. {@code position}
     * is the line with the coordinates, said to be approximate.
     */
    public record Group(double latitude, double longitude, String title, String position, List<Pin> gardens) {

        public Group {
            gardens = List.copyOf(gardens);
        }
    }

    /** The texts of the map that are not about one garden. */
    record Labels(String open, String edit, String delete, String altitude, String crops, String waiting,
                  String addHere, String unavailable, String water) {
    }

    /** What the map should centre on; nonce makes choosing the same garden twice a change again. */
    private record Centre(String id, int nonce) {
    }

    private Set<Long> known = Set.of();
    private boolean contributor;
    private int centreCount;

    GardenMap(String styleUrl) {
        setStyleUrl(styleUrl);
    }

    void setStyleUrl(String url) {
        getElement().setProperty("styleUrl", url == null ? "" : url);
    }

    void setLabels(Labels labels) {
        getElement().setPropertyBean("labels", labels);
    }

    /** The whole page's markers; also the ids a selection may name. */
    void setGroups(List<Group> groups) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Group group : groups) {
            for (Pin pin : group.gardens()) {
                ids.add(Long.parseLong(pin.id()));
            }
        }
        known = Set.copyOf(ids);
        getElement().setPropertyList("groups", groups);
    }

    /**
     * Adding, editing and deleting from the map need an OWNER or MEMBER (#33): a guest is never offered them,
     * and their events are refused here anyway.
     */
    void setContributor(boolean contributor) {
        this.contributor = contributor;
        getElement().setProperty("contributor", contributor);
    }

    /** Centres the map on a garden and opens its popup, when its row is selected in the table. */
    void focus(long gardenId) {
        if (!known.contains(gardenId)) {
            return;
        }
        getElement().setPropertyBean("centre", new Centre(Long.toString(gardenId), ++centreCount));
    }

    /** Moves the map to a place found by name and, for a contributor, offers to add a garden there. */
    void showPlace(double latitude, double longitude) {
        getElement().callJsFunction("showPlace", latitude, longitude);
    }

    /** The garden a click on a marker selects, when it is one of the gardens on this page. */
    public OptionalLong select(String gardenId) {
        long id;
        try {
            id = Long.parseLong(gardenId == null ? "" : gardenId.trim());
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
        return known.contains(id) ? OptionalLong.of(id) : OptionalLong.empty();
    }

    /** The garden a popup's Edit or Delete names, when it is one of this page's and the person may change it. */
    public OptionalLong change(String gardenId) {
        return contributor ? select(gardenId) : OptionalLong.empty();
    }

    /** The position a click on empty ground asks to add a garden at, when the person may and it is real. */
    public Optional<Point> point(double latitude, double longitude) {
        if (!contributor || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return Optional.empty();
        }
        return Optional.of(new Point(latitude, longitude));
    }

    public record Point(double latitude, double longitude) {
    }

    public boolean contributor() {
        return contributor;
    }

    /** A marker or a popup's name was chosen; the id is checked with {@link #select} before use. */
    Registration addSelectedListener(ComponentEventListener<GardenSelectedEvent> listener) {
        return addListener(GardenSelectedEvent.class, listener);
    }

    /** A popup's Open button was pressed; the id is checked with {@link #select} before use. */
    Registration addOpenListener(ComponentEventListener<GardenOpenEvent> listener) {
        return addListener(GardenOpenEvent.class, listener);
    }

    /** A popup's Edit button was pressed; the id is checked with {@link #change} before use. */
    Registration addEditListener(ComponentEventListener<GardenEditEvent> listener) {
        return addListener(GardenEditEvent.class, listener);
    }

    /** A popup's Delete button was pressed; the id is checked with {@link #change} before use. */
    Registration addDeleteListener(ComponentEventListener<GardenDeleteEvent> listener) {
        return addListener(GardenDeleteEvent.class, listener);
    }

    /** "Add a garden here" was pressed; the point is checked with {@link #point} before use. */
    Registration addAddHereListener(ComponentEventListener<AddHereEvent> listener) {
        return addListener(AddHereEvent.class, listener);
    }

    @DomEvent("garden-selected")
    public static class GardenSelectedEvent extends ComponentEvent<GardenMap> {

        private final String gardenId;

        public GardenSelectedEvent(GardenMap source, boolean fromClient,
                                   @EventData("event.detail.id") String gardenId) {
            super(source, fromClient);
            this.gardenId = gardenId;
        }

        public String gardenId() {
            return gardenId;
        }
    }

    @DomEvent("garden-open")
    public static class GardenOpenEvent extends ComponentEvent<GardenMap> {

        private final String gardenId;

        public GardenOpenEvent(GardenMap source, boolean fromClient,
                               @EventData("event.detail.id") String gardenId) {
            super(source, fromClient);
            this.gardenId = gardenId;
        }

        public String gardenId() {
            return gardenId;
        }
    }

    @DomEvent("garden-edit")
    public static class GardenEditEvent extends ComponentEvent<GardenMap> {

        private final String gardenId;

        public GardenEditEvent(GardenMap source, boolean fromClient,
                               @EventData("event.detail.id") String gardenId) {
            super(source, fromClient);
            this.gardenId = gardenId;
        }

        public String gardenId() {
            return gardenId;
        }
    }

    @DomEvent("garden-delete")
    public static class GardenDeleteEvent extends ComponentEvent<GardenMap> {

        private final String gardenId;

        public GardenDeleteEvent(GardenMap source, boolean fromClient,
                                 @EventData("event.detail.id") String gardenId) {
            super(source, fromClient);
            this.gardenId = gardenId;
        }

        public String gardenId() {
            return gardenId;
        }
    }

    @DomEvent("add-here")
    public static class AddHereEvent extends ComponentEvent<GardenMap> {

        private final double latitude;
        private final double longitude;

        public AddHereEvent(GardenMap source, boolean fromClient,
                            @EventData("event.detail.latitude") double latitude,
                            @EventData("event.detail.longitude") double longitude) {
            super(source, fromClient);
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double latitude() {
            return latitude;
        }

        public double longitude() {
            return longitude;
        }
    }
}
