package app.rootology.ui;

import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.GardenViews.PlotSummary;

import java.util.List;

/**
 * The four first steps of a garden (#81) and whether each is done: the garden has a place, a plot, something
 * growing and a question asked in it. Read from the services the garden home already uses, never stored.
 */
public record FirstSteps(boolean located, boolean hasPlot, boolean hasPlanting, boolean hasQuestion) {

    public static FirstSteps of(GardenSummary garden, List<PlotSummary> plots, List<PlantingSummary> growing,
                                boolean asked) {
        return new FirstSteps(garden.latitude() != null && garden.longitude() != null,
                !plots.isEmpty(), !growing.isEmpty(), asked);
    }

    /** The card is hidden once every step is done. */
    public boolean allDone() {
        return located && hasPlot && hasPlanting && hasQuestion;
    }
}
