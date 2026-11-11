package io.github.vincenzofiore.rootology.weather;

import io.github.vincenzofiore.rootology.resilience.CircuitBreaker;
import io.github.vincenzofiore.rootology.resilience.CircuitBreakers;
import io.github.vincenzofiore.rootology.resilience.ServiceUnavailableException;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The weather around a location: from the cache while it is fresh, otherwise from MET Norway. When the
 * service fails, a copy fetched less than a day ago is returned as stale; older than that, nothing.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    static final Duration STALE_LIMIT = Duration.ofHours(24);
    static final Duration PAST = Duration.ofDays(3);
    static final Duration KEPT = Duration.ofDays(30);

    /**
     * How old a stored copy may be to be shown as "now" (#67): the 7:00 alerts job fetches a new one every
     * morning, which keeps this true until the evening, and older than that the forecast is no longer today's.
     */
    static final Duration NOW_MAX_AGE = Duration.ofHours(12);

    /** How near a stored hour has to be to count as "now": forecasts are hourly near term (#67). */
    private static final Duration NOW_DISTANCE = Duration.ofHours(1);

    private final MetNorwayClient client;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final CircuitBreaker breaker;

    @Autowired
    WeatherService(MetNorwayClient client, JdbcClient jdbc, CircuitBreakers circuits) {
        this(client, jdbc, Clock.systemUTC(), circuits.create("MET Norway"));
    }

    WeatherService(MetNorwayClient client, JdbcClient jdbc, Clock clock, CircuitBreaker breaker) {
        this.client = client;
        this.jdbc = jdbc;
        this.clock = clock;
        this.breaker = breaker;
    }

    /** The last three days and the coming ones, or empty when there is no usable forecast. */
    @Transactional
    public Optional<Forecast> around(BigDecimal latitude, BigDecimal longitude) {
        BigDecimal lat = latitude.setScale(2, RoundingMode.HALF_UP);
        BigDecimal lon = longitude.setScale(2, RoundingMode.HALF_UP);
        Instant now = clock.instant();
        Optional<Cached> cached = jdbc.sql("SELECT fetched_at, expires_at, last_modified FROM weather_location "
                        + "WHERE latitude = ? AND longitude = ?").params(lat, lon)
                .query((rs, i) -> new Cached(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant(),
                        rs.getString(3))).optional();
        if (cached.isPresent() && now.isBefore(cached.get().expiresAt())) {
            return Optional.of(read(lat, lon, now, cached.get().fetchedAt(), false));
        }
        Timer.Sample call = breaker.startCall();
        try {
            if (!breaker.allowsCall()) {
                // Recently failing: do not wait for another timeout (#29).
                throw new ServiceUnavailableException("MET Norway circuit is open");
            }
            MetNorwayClient.Response response = client.fetch(lat.doubleValue(), lon.doubleValue(),
                    cached.map(Cached::lastModified).orElse(null));
            if (!response.notModified()) {
                store(lat, lon, response.hours(), now);
            }
            jdbc.sql("INSERT INTO weather_location (latitude, longitude, fetched_at, expires_at, last_modified) "
                            + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (latitude, longitude) DO UPDATE SET "
                            + "fetched_at = EXCLUDED.fetched_at, expires_at = EXCLUDED.expires_at, "
                            + "last_modified = EXCLUDED.last_modified")
                    .params(lat, lon, ts(now), ts(response.expires()), response.lastModified()).update();
            breaker.success(call);
            return Optional.of(read(lat, lon, now, now, false));
        } catch (ServiceUnavailableException e) {
            return fallback(cached, lat, lon, now);
        } catch (RuntimeException e) {
            breaker.failure(call);
            // The exception's message may carry the request URL, with the garden's coordinates (#27).
            log.warn("MET Norway unavailable: {}", e.getClass().getSimpleName());
            return fallback(cached, lat, lon, now);
        }
    }

    /**
     * The stored forecast hour nearest to now at this position, read only: what the gardens map's popups show
     * (#67). Empty when there is no stored copy, when it was fetched more than {@link #NOW_MAX_AGE} ago, or when
     * no stored hour is within an hour of now. It never calls MET Norway and never writes: the overview only
     * shows what questions and the 7:00 alerts job already stored.
     */
    public Optional<ForecastHour> storedNow(BigDecimal latitude, BigDecimal longitude) {
        BigDecimal lat = latitude.setScale(2, RoundingMode.HALF_UP);
        BigDecimal lon = longitude.setScale(2, RoundingMode.HALF_UP);
        Instant now = clock.instant();
        Optional<Instant> fetchedAt = jdbc.sql("SELECT fetched_at FROM weather_location "
                        + "WHERE latitude = ? AND longitude = ?").params(lat, lon)
                .query((rs, i) -> rs.getTimestamp(1).toInstant()).optional();
        if (fetchedAt.isEmpty() || Duration.between(fetchedAt.get(), now).compareTo(NOW_MAX_AGE) >= 0) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT valid_at, air_temperature, relative_humidity, precipitation_mm, step_hours, "
                        + "symbol_code FROM weather_hour WHERE latitude = ? AND longitude = ? "
                        + "AND valid_at BETWEEN ? AND ?")
                .params(lat, lon, ts(now.minus(NOW_DISTANCE)), ts(now.plus(NOW_DISTANCE)))
                .query(WeatherService::hour).list().stream()
                .min(Comparator.comparingLong(hour -> Math.abs(Duration.between(hour.time(), now).toMillis())));
    }

    /** A copy fetched less than a day ago, marked stale; otherwise no weather. */
    private Optional<Forecast> fallback(Optional<Cached> cached, BigDecimal lat, BigDecimal lon, Instant now) {
        return cached.filter(c -> Duration.between(c.fetchedAt(), now).compareTo(STALE_LIMIT) < 0)
                .map(c -> read(lat, lon, now, c.fetchedAt(), true));
    }

    private void store(BigDecimal lat, BigDecimal lon, List<ForecastHour> hours, Instant now) {
        for (ForecastHour h : hours) {
            jdbc.sql("INSERT INTO weather_hour (latitude, longitude, valid_at, air_temperature, relative_humidity, "
                            + "precipitation_mm, step_hours, symbol_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT (latitude, longitude, valid_at) DO UPDATE SET "
                            + "air_temperature = EXCLUDED.air_temperature, relative_humidity = EXCLUDED.relative_humidity, "
                            + "precipitation_mm = EXCLUDED.precipitation_mm, step_hours = EXCLUDED.step_hours, "
                            + "symbol_code = EXCLUDED.symbol_code")
                    .params(lat, lon, ts(h.time()), h.airTemperature(), h.relativeHumidity(), h.precipitation(),
                            h.stepHours(), h.symbol()).update();
        }
        jdbc.sql("DELETE FROM weather_hour WHERE valid_at < ?").params(ts(now.minus(KEPT))).update();
    }

    private Forecast read(BigDecimal lat, BigDecimal lon, Instant now, Instant fetchedAt, boolean stale) {
        List<ForecastHour> hours = jdbc.sql("SELECT valid_at, air_temperature, relative_humidity, precipitation_mm, "
                        + "step_hours, symbol_code FROM weather_hour WHERE latitude = ? AND longitude = ? AND valid_at >= ? "
                        + "ORDER BY valid_at").params(lat, lon, ts(now.minus(PAST)))
                .query(WeatherService::hour).list();
        return new Forecast(hours, fetchedAt, stale);
    }

    /** One stored hour, in the column order every query above selects. */
    private static ForecastHour hour(ResultSet rs, int row) throws SQLException {
        return new ForecastHour(rs.getTimestamp(1).toInstant(), rs.getDouble(2),
                (Double) rs.getObject(3), (Double) rs.getObject(4), rs.getInt(5), rs.getString(6));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private record Cached(Instant fetchedAt, Instant expiresAt, String lastModified) {
    }
}
