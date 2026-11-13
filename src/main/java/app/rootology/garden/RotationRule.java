package app.rootology.garden;

import app.rootology.facts.Crop;
import app.rootology.facts.CropCatalog;
import app.rootology.garden.GardenViews.PlantingSummary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Rule `family-rotation-v1`: in the same plot, avoid following a crop with one of the same botanical
 * family (shared pests, diseases and nutrient demands). It looks at the most recent planting in the
 * plot that started before the candidate date. The result is an inferred warning, never a fact
 * of the knowledge base, and it names the rule and the plantings it comes from.
 */
@Component
public class RotationRule {

    public static final String ID = "family-rotation-v1";

    /** Why the candidate crop should not follow the previous one in this plot. */
    public record RotationWarning(String rule, String family, PlantingSummary previous, String candidateCropId,
                                  String reason) {
    }

    private final CropCatalog crops;

    public RotationRule(CropCatalog crops) {
        this.crops = crops;
    }

    /** plotHistory: every planting of one plot, in any order. */
    public Optional<RotationWarning> check(List<PlantingSummary> plotHistory, String candidateCropId, LocalDate on) {
        Optional<String> family = crops.byId(candidateCropId).map(Crop::family);
        Optional<PlantingSummary> previous = plotHistory.stream()
                .filter(p -> p.plantedOn().isBefore(on) || p.plantedOn().isEqual(on))
                .max(Comparator.comparing(PlantingSummary::plantedOn));
        if (family.isEmpty() || previous.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> previousFamily = crops.byId(previous.get().cropId()).map(Crop::family);
        if (!family.equals(previousFamily)) {
            return Optional.empty();
        }
        String candidateName = crops.byId(candidateCropId).map(Crop::italianName).orElse(candidateCropId);
        return Optional.of(new RotationWarning(ID, family.get(), previous.get(), candidateCropId,
                candidateName + " and " + previous.get().cropName() + " (since " + previous.get().plantedOn()
                        + ") are both " + family.get() + ": avoid the same family in succession in "
                        + previous.get().plotName() + "."));
    }
}
