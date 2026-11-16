package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.shared.Registration;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The plan of a garden (#90): a thin Java side over the web component in {@code frontend/garden-plan.ts},
 * which draws the plan and its areas in SVG. The page says what to show, already worded; the component only
 * draws it and reports back, each report checked here before it is let through: an area was chosen, or moved
 * or resized by dragging or with the keyboard.
 */
@Tag("rootology-garden-plan")
@JsModule("./garden-plan.ts")
public final class GardenPlanBoard extends Component {

    /**
     * One area on the plan: its name, the drawing of its kind, its size and its aria label already worded, and
     * where it sits in metres from the plan's top-left corner.
     */
    public record Box(String id, String name, String icon, String size, String label, double x, double y,
                      double width, double depth) {
    }

    /** The plan's own size in metres; {@code nonce} makes a repeated selection a change again. */
    private record Plan(double width, double depth) {
    }

    private record Selected(String id, int nonce) {
    }

    /** An area the person may change, wholly inside the plan and rounded to the decimetre. */
    public record Move(long areaId, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal depth) {
    }

    private Set<Long> known = Set.of();
    private double planWidth;
    private double planDepth;
    private boolean contributor;
    private int selectCount;

    GardenPlanBoard() {
        setContributor(false);
    }

    /** The plan to draw, in metres; the page draws it first and the areas second. */
    void setPlan(double width, double depth) {
        planWidth = width;
        planDepth = depth;
        getElement().setPropertyBean("plan", new Plan(width, depth));
    }

    /** The areas on the plan; also the ids a selection or a move may name. */
    void setBoxes(List<Box> boxes) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Box box : boxes) {
            ids.add(Long.parseLong(box.id()));
        }
        known = Set.copyOf(ids);
        getElement().setPropertyList("boxes", boxes);
    }

    /**
     * Moving and resizing need an OWNER or MEMBER (#33): a guest only looks, is never offered a handle, and
     * their events are refused here anyway.
     */
    void setContributor(boolean contributor) {
        this.contributor = contributor;
        getElement().setProperty("contributor", contributor);
    }

    /** What a screen reader calls the drawing as a whole. */
    void setPlanLabel(String label) {
        getElement().setProperty("planLabel", label == null ? "" : label);
    }

    /** Marks an area on the plan, when its card is chosen. */
    void select(long areaId) {
        if (!known.contains(areaId)) {
            return;
        }
        getElement().setPropertyBean("selected", new Selected(Long.toString(areaId), ++selectCount));
    }

    /** The area a tap on the plan names, when it is one of the areas on this plan. */
    public OptionalLong selected(String boxId) {
        OptionalLong id = parse(boxId);
        return id.isPresent() && known.contains(id.getAsLong()) ? id : OptionalLong.empty();
    }

    /**
     * The change a drag or a key names, when the person may change the plan and the change is one the service
     * would store anyway: an area of this plan, in metres, inside the plan. The service rounds and checks again.
     */
    public Optional<Move> move(String boxId, double x, double y, double width, double depth) {
        OptionalLong id = selected(boxId);
        if (!contributor || id.isEmpty() || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || !Double.isFinite(depth)) {
            return Optional.empty();
        }
        double min = app.rootology.garden.GardenService.MIN_AREA_SIDE.doubleValue();
        double tolerance = 0.05;
        if (x < -tolerance || y < -tolerance || width < min - tolerance || depth < min - tolerance
                || x + width > planWidth + tolerance || y + depth > planDepth + tolerance) {
            return Optional.empty();
        }
        return Optional.of(new Move(id.getAsLong(), metres(x), metres(y), metres(width), metres(depth)));
    }

    private static OptionalLong parse(String id) {
        try {
            return OptionalLong.of(Long.parseLong(id == null ? "" : id.trim()));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** A length rounded to the centimetre, as the service stores it. */
    private static BigDecimal metres(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public boolean contributor() {
        return contributor;
    }

    /** An area was tapped: either its box on the plan, or its card below. */
    Registration addAreaSelectedListener(ComponentEventListener<AreaSelectedEvent> listener) {
        return addListener(AreaSelectedEvent.class, listener);
    }

    /** A box was dragged, resized or moved with the keys, and let go. */
    Registration addAreaMovedListener(ComponentEventListener<AreaMovedEvent> listener) {
        return addListener(AreaMovedEvent.class, listener);
    }

    @DomEvent("area-selected")
    public static class AreaSelectedEvent extends ComponentEvent<GardenPlanBoard> {

        private final String areaId;

        public AreaSelectedEvent(GardenPlanBoard source, boolean fromClient,
                                 @EventData("event.detail.id") String areaId) {
            super(source, fromClient);
            this.areaId = areaId;
        }

        public String areaId() {
            return areaId;
        }
    }

    @DomEvent("area-moved")
    public static class AreaMovedEvent extends ComponentEvent<GardenPlanBoard> {

        private final String areaId;
        private final double x;
        private final double y;
        private final double width;
        private final double depth;

        public AreaMovedEvent(GardenPlanBoard source, boolean fromClient,
                              @EventData("event.detail.id") String areaId,
                              @EventData("event.detail.x") double x,
                              @EventData("event.detail.y") double y,
                              @EventData("event.detail.width") double width,
                              @EventData("event.detail.depth") double depth) {
            super(source, fromClient);
            this.areaId = areaId;
            this.x = x;
            this.y = y;
            this.width = width;
            this.depth = depth;
        }

        public String areaId() {
            return areaId;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double width() {
            return width;
        }

        public double depth() {
            return depth;
        }
    }
}
