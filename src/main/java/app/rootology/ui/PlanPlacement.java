package app.rootology.ui;

import app.rootology.garden.GardenService;
import app.rootology.garden.PlotKind;

import java.math.BigDecimal;
import java.util.List;

/**
 * Where an area goes when it is put on the plan (#90): the size it takes by default and the first free spot
 * for it. A small piece of geometry, kept out of the page so it can be tested on its own, in metres.
 */
final class PlanPlacement {

    /** The smallest side of an area, as the service has it: a small pot. */
    private static final double MIN_SIDE = GardenService.MIN_AREA_SIDE.doubleValue();

    /** One rectangle on the plan: its top-left corner from the plan's top-left, and its size, in metres. */
    record Box(double x, double y, double width, double depth) {

        /** Whether two boxes share some ground; boxes that only touch are beside each other, not on top. */
        boolean overlaps(Box other) {
            return x < other.x + other.width - EPSILON && other.x < x + width - EPSILON
                    && y < other.y + other.depth - EPSILON && other.y < y + depth - EPSILON;
        }
    }

    /** A tolerance in metres, so a box that starts where another ends is not read as overlapping. */
    private static final double EPSILON = 1e-6;

    /** The step a spot is searched at and a size is rounded to: a decimetre. */
    private static final double STEP = 0.1;

    /**
     * The size an area takes when it first goes on the plan: a square of its declared surface when it has one,
     * otherwise the size of its kind (a pot is small, a greenhouse is not). Clamped inside the plan.
     */
    static Box defaultSize(PlotKind kind, BigDecimal areaM2, double planWidth, double planDepth) {
        double width;
        double depth;
        if (areaM2 != null && areaM2.signum() > 0) {
            double side = snap(Math.sqrt(areaM2.doubleValue()));
            width = side;
            depth = side;
        } else {
            width = kindWidth(kind);
            depth = kindDepth(kind);
        }
        return new Box(0, 0, clamp(width, MIN_SIDE, Math.max(MIN_SIDE, planWidth)),
                clamp(depth, MIN_SIDE, Math.max(MIN_SIDE, planDepth)));
    }

    private static double kindWidth(PlotKind kind) {
        return switch (kind) {
            case POT -> 0.4;
            case PLANTER -> 1;
            case RAISED_BED -> 1.2;
            case GREENHOUSE -> 3;
            case GROUND -> 2;
        };
    }

    private static double kindDepth(PlotKind kind) {
        return switch (kind) {
            case POT -> 0.4;
            case PLANTER -> 0.4;
            case RAISED_BED -> 0.8;
            case GREENHOUSE -> 2;
            case GROUND -> 1;
        };
    }

    /**
     * The first free spot for an area of that size, scanning the plan from its top-left in decimetres, so the
     * areas end up packed from a corner. With no free spot at all, the top-left corner: the person moves it.
     */
    static Box firstFree(Box size, List<Box> placed, double planWidth, double planDepth) {
        int planRight = tenths(planWidth);
        int planBottom = tenths(planDepth);
        int width = (int) clamp(tenths(size.width()), 1, Math.max(1, planRight));
        int depth = (int) clamp(tenths(size.depth()), 1, Math.max(1, planBottom));
        for (int y = 0; y + depth <= planBottom; y++) {
            for (int x = 0; x + width <= planRight; x++) {
                Box candidate = new Box(x / 10.0, y / 10.0, width / 10.0, depth / 10.0);
                if (placed.stream().noneMatch(candidate::overlaps)) {
                    return candidate;
                }
            }
        }
        return new Box(0, 0, width / 10.0, depth / 10.0);
    }

    /** A length in metres rounded to the decimetre, never closer than the smallest side. */
    private static double snap(double value) {
        return Math.max(MIN_SIDE, Math.round(value * 10) / 10.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    private static int tenths(double metres) {
        return (int) Math.round(metres * 10);
    }

    private PlanPlacement() {
    }
}
