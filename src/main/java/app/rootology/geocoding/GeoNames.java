package app.rootology.geocoding;

import app.rootology.resilience.CircuitBreaker;
import app.rootology.resilience.CircuitBreakers;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Searches GeoNames (https://www.geonames.org/export/geonames-search.html): places by the start of their
 * name, and the nearest populated place to a position (https://www.geonames.org/export/web-services.html). The free web services allow commercial use, within 10,000
 * credits a day and 1,000 an hour per account; searches are cached to spend few. Every failure gives an
 * empty result: a garden can always be saved with the browser's position or typed coordinates.
 */
@Service
public class GeoNames {

    static final int MIN_LENGTH = 3;
    static final int MAX_ROWS = 8;
    static final int CACHE_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(GeoNames.class);

    private final RestClient restClient;
    private final String username;
    private final CircuitBreaker breaker;
    private final Map<String, List<Place>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Place>> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    @Autowired
    GeoNames(@Value("${rootology.geonames.base-url}") String baseUrl,
             @Value("${rootology.geonames.username:}") String username, CircuitBreakers circuits) {
        this(RestClient.builder().requestFactory(timeouts()).baseUrl(baseUrl).build(), username,
                circuits.create("GeoNames"));
    }

    GeoNames(RestClient restClient, String username, CircuitBreaker breaker) {
        this.restClient = restClient;
        this.username = username == null ? "" : username.trim();
        this.breaker = breaker;
    }

    /** False when no GeoNames account is configured: place search is then not offered. */
    public boolean enabled() {
        return !username.isEmpty();
    }

    /** Populated places whose name starts with the text (at least 3 letters), most relevant first. */
    public List<Place> search(String text, String language) {
        String prefix = text == null ? "" : text.trim();
        if (!enabled() || prefix.length() < MIN_LENGTH) {
            return List.of();
        }
        String lang = language == null || language.isBlank() ? "en" : language.toLowerCase(Locale.ROOT);
        String key = lang + "|" + prefix.toLowerCase(Locale.ROOT);
        List<Place> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<Place> places = call(() -> restClient.get()
                .uri(u -> u.path("/searchJSON").queryParam("name_startsWith", prefix).queryParam("featureClass", "P")
                        .queryParam("maxRows", MAX_ROWS).queryParam("orderby", "relevance").queryParam("lang", lang)
                        .queryParam("style", "MEDIUM").queryParam("username", username).build())
                .retrieve().body(SearchResult.class))
                .map(r -> r.geonames() == null ? List.<Place>of() : r.geonames().stream()
                        .map(g -> new Place(g.name(), g.adminName1(), g.countryCode(), g.countryName(),
                                Double.parseDouble(g.lat()), Double.parseDouble(g.lng())))
                        .toList())
                .orElse(null);
        if (places == null) {
            return List.of();
        }
        cache.put(key, places);
        return places;
    }

    /**
     * The populated place nearest to a position, with its region and country; empty when there is none
     * nearby (for example at sea) or GeoNames cannot be reached. The coordinates returned are the position's.
     */
    public Optional<Place> nearestPlace(double latitude, double longitude, String language) {
        if (!enabled()) {
            return Optional.empty();
        }
        String lang = language == null || language.isBlank() ? "en" : language.toLowerCase(Locale.ROOT);
        return call(() -> restClient.get()
                .uri(u -> u.path("/findNearbyPlaceNameJSON")
                        .queryParam("lat", String.format(Locale.ROOT, "%.2f", latitude))
                        .queryParam("lng", String.format(Locale.ROOT, "%.2f", longitude))
                        .queryParam("lang", lang).queryParam("style", "MEDIUM")
                        .queryParam("username", username).build())
                .retrieve().body(SearchResult.class))
                .flatMap(r -> r.geonames() == null ? Optional.empty() : r.geonames().stream().findFirst())
                .filter(g -> g.countryCode() != null && g.countryCode().matches("[A-Z]{2}"))
                .map(g -> new Place(g.name(), g.adminName1(), g.countryCode(), g.countryName(), latitude, longitude));
    }

    /**
     * The elevation in metres of a position from GTOPO30 (USGS, public domain; about 1 km, imprecise on
     * coasts), for the garden's climate profile (#62). Empty when GeoNames cannot be reached or the
     * position has no value (-9999); saving a garden never depends on it.
     */
    public Optional<Integer> elevation(double latitude, double longitude) {
        if (!enabled()) {
            return Optional.empty();
        }
        return call(() -> restClient.get()
                        .uri(u -> u.path("/gtopo30JSON")
                                .queryParam("lat", String.format(Locale.ROOT, "%.2f", latitude))
                                .queryParam("lng", String.format(Locale.ROOT, "%.2f", longitude))
                                .queryParam("username", username).build())
                        .retrieve().body(ElevationResult.class))
                .map(ElevationResult::gtopo30)
                .filter(meters -> meters != null && meters != -9999);
    }

    /** One call through the breaker; GeoNames reports errors in the body with a "status". */
    private <T extends Reply> Optional<T> call(java.util.function.Supplier<T> request) {
        if (!breaker.allowsCall()) {
            return Optional.empty();
        }
        Timer.Sample call = breaker.startCall();
        try {
            T reply = request.get();
            if (reply == null) {
                throw new IllegalStateException("empty reply");
            }
            if (reply.status() != null) {
                // 15: no result (for example at sea); anything else is a service problem.
                if (reply.status().value() == 15) {
                    breaker.success(call);
                    return Optional.empty();
                }
                throw new IllegalStateException("GeoNames: " + reply.status().message());
            }
            breaker.success(call);
            return Optional.of(reply);
        } catch (RuntimeException e) {
            breaker.failure(call);
            // The exception's message may carry the request URL, with the account and the place searched (#27).
            log.warn("GeoNames call failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        return timeouts;
    }

    interface Reply {
        Status status();
    }

    record Status(String message, int value) {
    }

    record SearchResult(List<Entry> geonames, Status status) implements Reply {
    }

    record ElevationResult(Integer gtopo30, Status status) implements Reply {
    }

    record Entry(String name, String adminName1, String countryCode, String countryName, String lat, String lng) {
    }
}
