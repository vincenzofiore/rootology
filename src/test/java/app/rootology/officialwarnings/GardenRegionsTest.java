package app.rootology.officialwarnings;

import app.rootology.garden.GardenLocated;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.geocoding.GeoNames;
import app.rootology.geocoding.Place;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The region lookup against a stubbed GeoNames: no network (#136). */
class GardenRegionsTest {

    private final GardenService gardens = mock(GardenService.class);
    private final GeoNames geoNames = mock(GeoNames.class);
    private final GardenRegions regions = new GardenRegions(gardens, geoNames);

    private static Place place(String countryCode, String adminCode1) {
        return new Place("Milano", "Lombardia", countryCode, "Italia", 45.46, 9.19, 0, adminCode1);
    }

    @Test
    void aLocatedGardenGetsTheRegionOfItsPlace() {
        when(geoNames.nearestPlace(anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.of(place("IT", "09")));

        regions.on(new GardenLocated(7, new BigDecimal("45.46"), new BigDecimal("9.19"), 120));

        verify(gardens).setRegion(7, "09");
    }

    @Test
    void aForeignPlaceGetsNoItalianRegion() {
        when(geoNames.nearestPlace(anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.of(place("FR", "11")));

        regions.on(new GardenLocated(7, new BigDecimal("45.76"), new BigDecimal("4.84"), 120));

        verify(gardens, never()).setRegion(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void anEmptyGeonamesAnswerLeavesTheRegionUnset() {
        when(geoNames.nearestPlace(anyDouble(), anyDouble(), anyString())).thenReturn(Optional.empty());

        regions.on(new GardenLocated(7, new BigDecimal("40.00"), new BigDecimal("12.00"), null));

        verify(gardens, never()).setRegion(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void theNightlyRunFillsTheGardensWithoutRegion() {
        when(gardens.withoutRegion()).thenReturn(List.of(
                new GardenSummary(1, "Orto", null, new BigDecimal("45.46"), new BigDecimal("9.19"), "IT", null, 120, null)));
        when(geoNames.nearestPlace(anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.of(place("IT", "09")));

        regions.fillMissing();

        verify(gardens).setRegion(1, "09");
    }
}
