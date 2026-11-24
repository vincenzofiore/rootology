package app.rootology.garden;

import app.rootology.facts.ClimateCells;
import app.rootology.facts.FrostSeasonRule;
import app.rootology.facts.ReferenceSite;
import app.rootology.facts.SiteProfile;
import app.rootology.garden.GardenViews.GardenSummary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The climate profile a garden's calendars read (#62): its coordinates pick the nearest AgERA5 cell and its
 * altitude corrects the cell's frost dates. A garden without coordinates, or with no land cell within reach,
 * has no profile of its own, and the default is the one the pipeline uses: Vasto (CENTRO).
 *
 * <p>One place for the rule so the pipeline's {@code ContextResolver} and the crop proposal rule
 * (`crop-proposal-v1`, #141) read the same site.
 */
@Component
public class GardenClimate {

    private final ClimateCells cells;

    public GardenClimate(ClimateCells cells) {
        this.cells = cells;
    }

    /** The garden's own profile, empty when it has no coordinates or no cell within reach. */
    public Optional<SiteProfile> profile(GardenSummary garden) {
        if (garden == null || garden.latitude() == null || garden.longitude() == null) {
            return Optional.empty();
        }
        return cells.nearest(garden.latitude().doubleValue(), garden.longitude().doubleValue())
                .map(cell -> FrostSeasonRule.profile(cell, garden.elevationM(), null));
    }

    /** The site a calendar question uses when the garden has no profile: Vasto (CENTRO). */
    public SiteProfile defaultProfile() {
        return ReferenceSite.CENTRO.profile(cells);
    }
}