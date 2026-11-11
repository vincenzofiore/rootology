package io.github.vincenzofiore.rootology.alerts;

import io.github.vincenzofiore.rootology.garden.GardenLocated;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.GrowingGarden;
import io.github.vincenzofiore.rootology.weather.WeatherService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every garden's place keeps a stored forecast for the map's popups (#67), new places at once. */
class WeatherRefreshTest {

    private final GardenService gardens = mock(GardenService.class);
    private final WeatherService weather = mock(WeatherService.class);
    private final WeatherRefresh refresh = new WeatherRefresh(gardens, weather,
            Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneId.of("Europe/Rome")));

    private static GrowingGarden garden(long id, String latitude, String longitude) {
        return new GrowingGarden(1, new GardenSummary(id, "Orto " + id, null, new BigDecimal(latitude),
                new BigDecimal(longitude), "IT", null), List.of());
    }

    @Test
    void everyPlaceWithAGardenIsRefreshedOnceEvenWithNothingGrowing() {
        when(gardens.locatedGardensGrowing(any())).thenReturn(List.of(garden(1, "42.11", "14.71"),
                garden(2, "42.11", "14.71"), garden(3, "45.07", "7.69")));

        assertThat(refresh.refresh()).isEqualTo(2);

        verify(weather).around(new BigDecimal("42.11"), new BigDecimal("14.71"));
        verify(weather).around(new BigDecimal("45.07"), new BigDecimal("7.69"));
        verify(weather, times(2)).around(any(), any());
    }

    @Test
    void aGardenWithANewPlaceGetsItsForecast() {
        refresh.on(new GardenLocated(7, new BigDecimal("38.12"), new BigDecimal("13.36")));

        verify(weather).around(new BigDecimal("38.12"), new BigDecimal("13.36"));
    }
}
