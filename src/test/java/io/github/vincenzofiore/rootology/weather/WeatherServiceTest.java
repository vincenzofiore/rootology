package io.github.vincenzofiore.rootology.weather;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.resilience.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The cache and the fallbacks, with MET Norway replaced by a mock. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WeatherServiceTest {

    @Autowired
    WeatherService weather;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    MetNorwayClient client;

    private static List<ForecastHour> comingHours() {
        Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
        return List.of(new ForecastHour(hour, 4.0, 90.0, 0.0, 1, "fog"),
                new ForecastHour(hour.plus(1, ChronoUnit.HOURS), 3.0, 92.0, 0.2, 6));
    }

    private void expire(String lat, String lon) {
        jdbc.sql("UPDATE weather_location SET expires_at = ? WHERE latitude = ? AND longitude = ?")
                .params(Timestamp.from(Instant.now().minusSeconds(60)), new BigDecimal(lat), new BigDecimal(lon))
                .update();
    }

    @Test
    void aFreshForecastIsServedFromTheCache() {
        when(client.fetch(anyDouble(), anyDouble(), isNull())).thenReturn(new MetNorwayClient.Response(comingHours(),
                Instant.now().plusSeconds(1800), "Wed, 04 Nov 2026 09:30:30 GMT"));

        Forecast first = weather.around(new BigDecimal("45.46"), new BigDecimal("9.19")).orElseThrow();
        Forecast second = weather.around(new BigDecimal("45.4600"), new BigDecimal("9.1900")).orElseThrow();

        assertThat(first.hours()).extracting(ForecastHour::airTemperature).containsExactly(4.0, 3.0);
        assertThat(first.hours()).extracting(ForecastHour::symbol).containsExactly("fog", null);
        assertThat(second.hours()).isEqualTo(first.hours());
        assertThat(second.stale()).isFalse();
        verify(client, times(1)).fetch(anyDouble(), anyDouble(), isNull());
    }

    @Test
    void anExpiredForecastIsRevalidatedWithItsLastModified() {
        when(client.fetch(eq(41.9), eq(12.5), isNull())).thenReturn(new MetNorwayClient.Response(comingHours(),
                Instant.now().plusSeconds(1800), "Wed, 04 Nov 2026 09:30:30 GMT"));
        weather.around(new BigDecimal("41.90"), new BigDecimal("12.50"));
        expire("41.90", "12.50");
        when(client.fetch(41.9, 12.5, "Wed, 04 Nov 2026 09:30:30 GMT")).thenReturn(new MetNorwayClient.Response(
                null, Instant.now().plusSeconds(1800), "Wed, 04 Nov 2026 09:30:30 GMT"));

        Forecast again = weather.around(new BigDecimal("41.90"), new BigDecimal("12.50")).orElseThrow();

        assertThat(again.hours()).hasSize(2);
        assertThat(again.stale()).isFalse();
    }

    @Test
    void whenTheServiceFailsARecentCopyIsUsedAsStale() {
        when(client.fetch(eq(40.85), eq(14.27), isNull())).thenReturn(new MetNorwayClient.Response(comingHours(),
                Instant.now().plusSeconds(1800), null));
        weather.around(new BigDecimal("40.85"), new BigDecimal("14.27"));
        expire("40.85", "14.27");
        when(client.fetch(eq(40.85), eq(14.27), isNull())).thenThrow(new IllegalStateException("down"));

        Forecast stale = weather.around(new BigDecimal("40.85"), new BigDecimal("14.27")).orElseThrow();

        assertThat(stale.stale()).isTrue();
        assertThat(stale.hours()).hasSize(2);
    }

    @Test
    void anOpenCircuitSkipsTheServiceAndUsesTheStoredCopy() {
        CircuitBreaker breaker =
                new CircuitBreaker("test MET");
        WeatherService service = new WeatherService(client, jdbc, java.time.Clock.systemUTC(), breaker);
        when(client.fetch(eq(43.77), eq(11.25), isNull())).thenReturn(new MetNorwayClient.Response(comingHours(),
                Instant.now().plusSeconds(1800), null));
        service.around(new BigDecimal("43.77"), new BigDecimal("11.25"));
        expire("43.77", "11.25");
        when(client.fetch(eq(43.77), eq(11.25), isNull())).thenThrow(new IllegalStateException("down"));

        for (int i = 0; i < 5; i++) {
            assertThat(service.around(new BigDecimal("43.77"), new BigDecimal("11.25"))).get()
                    .extracting(Forecast::stale).isEqualTo(true);
        }

        // One successful call, then three failures; the last two questions did not call MET Norway.
        verify(client, times(4)).fetch(eq(43.77), eq(11.25), isNull());
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    /** A service on a fixed clock, so "now" and the copy's age are exact (#67). */
    private WeatherService at(Instant now, CircuitBreaker breaker) {
        return new WeatherService(client, jdbc, Clock.fixed(now, ZoneOffset.UTC), breaker);
    }

    /** A stored copy and its hours, inserted directly: the overview only ever reads what is there (#67). */
    private void store(String lat, String lon, Instant fetchedAt, ForecastHour... hours) {
        BigDecimal latitude = new BigDecimal(lat);
        BigDecimal longitude = new BigDecimal(lon);
        jdbc.sql("INSERT INTO weather_location (latitude, longitude, fetched_at, expires_at, last_modified) "
                        + "VALUES (?, ?, ?, ?, NULL) ON CONFLICT (latitude, longitude) DO UPDATE SET "
                        + "fetched_at = EXCLUDED.fetched_at, expires_at = EXCLUDED.expires_at")
                .params(latitude, longitude, Timestamp.from(fetchedAt),
                        Timestamp.from(fetchedAt.plusSeconds(1800))).update();
        for (ForecastHour hour : hours) {
            jdbc.sql("INSERT INTO weather_hour (latitude, longitude, valid_at, air_temperature, relative_humidity, "
                            + "precipitation_mm, step_hours, symbol_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT (latitude, longitude, valid_at) DO UPDATE SET "
                            + "air_temperature = EXCLUDED.air_temperature, precipitation_mm = EXCLUDED.precipitation_mm, "
                            + "step_hours = EXCLUDED.step_hours, symbol_code = EXCLUDED.symbol_code")
                    .params(latitude, longitude, Timestamp.from(hour.time()), hour.airTemperature(),
                            hour.relativeHumidity(), hour.precipitation(), hour.stepHours(), hour.symbol()).update();
        }
    }

    @Test
    void theStoredHourNearestToNowIsShownFromAFreshCopy() {
        Instant now = Instant.parse("2026-11-11T10:00:00Z");
        CircuitBreaker breaker =
                new CircuitBreaker("test now");
        WeatherService service = at(now, breaker);
        store("45.47", "9.21", now.minus(1, ChronoUnit.HOURS),
                new ForecastHour(now.minus(50, ChronoUnit.MINUTES), 18.0, null, null, 1, "cloudy"),
                new ForecastHour(now.plus(40, ChronoUnit.MINUTES), 12.5, null, 0.4, 1, "rain"));

        ForecastHour hour = service.storedNow(new BigDecimal("45.4700"), new BigDecimal("9.2100")).orElseThrow();

        assertThat(hour.time()).isEqualTo(now.plus(40, ChronoUnit.MINUTES));
        assertThat(hour.airTemperature()).isEqualTo(12.5);
        assertThat(hour.symbol()).isEqualTo("rain");
        // Reading what is stored never calls MET Norway and never touches the breaker (#67).
        verifyNoInteractions(client);
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void aPositionWithNoStoredCopyHasNoWeatherNow() {
        Instant now = Instant.parse("2026-11-11T10:00:00Z");

        assertThat(at(now, new CircuitBreaker("test none"))
                .storedNow(new BigDecimal("37.11"), new BigDecimal("15.29"))).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void aCopyOlderThanTwelveHoursHasNoWeatherNow() {
        Instant now = Instant.parse("2026-11-11T10:00:00Z");
        WeatherService service = at(now, new CircuitBreaker("test old"));
        store("44.11", "12.29", now.minus(13, ChronoUnit.HOURS),
                new ForecastHour(now, 9.0, null, null, 1, "fair"));

        assertThat(service.storedNow(new BigDecimal("44.11"), new BigDecimal("12.29"))).isEmpty();
    }

    @Test
    void noStoredHourNearNowMeansNoWeatherNow() {
        Instant now = Instant.parse("2026-11-11T10:00:00Z");
        WeatherService service = at(now, new CircuitBreaker("test far"));
        store("43.11", "11.29", now.minus(1, ChronoUnit.HOURS),
                new ForecastHour(now.plus(3, ChronoUnit.HOURS), 7.0, null, null, 6, "fair"));

        assertThat(service.storedNow(new BigDecimal("43.11"), new BigDecimal("11.29"))).isEmpty();
    }

    @Test
    void withoutAnyCopyAFailureMeansNoWeather() {
        when(client.fetch(anyDouble(), anyDouble(), isNull())).thenThrow(new IllegalStateException("down"));

        assertThat(weather.around(new BigDecimal("38.12"), new BigDecimal("13.36"))).isEmpty();
    }
}
