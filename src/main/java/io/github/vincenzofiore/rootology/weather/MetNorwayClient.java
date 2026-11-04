package io.github.vincenzofiore.rootology.weather;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Calls MET Norway's Locationforecast 2.0 (compact). Its terms: identify the app in the User-Agent,
 * respect `Expires`, send `If-Modified-Since`, at most 4 decimals in coordinates
 * (https://api.met.no/doc/TermsOfService).
 */
@Component
class MetNorwayClient {

    /** A new forecast, or none when it did not change since lastModified (HTTP 304). */
    record Response(List<ForecastHour> hours, Instant expires, String lastModified) {

        boolean notModified() {
            return hours == null;
        }
    }

    private final RestClient restClient;

    @Autowired
    MetNorwayClient(@Value("${rootology.weather.base-url}") String baseUrl,
                    @Value("${rootology.weather.user-agent}") String userAgent) {
        this(RestClient.builder().requestFactory(timeouts()).baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent).build());
    }

    MetNorwayClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Short timeouts: an answer without weather is better than a question that hangs. */
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        return timeouts;
    }

    Response fetch(double latitude, double longitude, String lastModified) {
        return restClient.get()
                .uri(u -> u.path("/weatherapi/locationforecast/2.0/compact")
                        .queryParam("lat", String.format(Locale.ROOT, "%.2f", latitude))
                        .queryParam("lon", String.format(Locale.ROOT, "%.2f", longitude)).build())
                .headers(h -> {
                    if (lastModified != null) {
                        h.set(HttpHeaders.IF_MODIFIED_SINCE, lastModified);
                    }
                })
                .exchange((request, response) -> {
                    Instant expires = httpDate(response.getHeaders().getFirst(HttpHeaders.EXPIRES));
                    String modified = response.getHeaders().getFirst(HttpHeaders.LAST_MODIFIED);
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_MODIFIED)) {
                        return new Response(null, expires, lastModified);
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("MET Norway answered " + response.getStatusCode());
                    }
                    return new Response(hours(response.bodyTo(Body.class)), expires, modified);
                });
    }

    static List<ForecastHour> hours(Body body) {
        List<Step> steps = body.properties().timeseries();
        List<ForecastHour> hours = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            Instant time = Instant.parse(s.time());
            int step = i + 1 < steps.size()
                    ? (int) Duration.between(time, Instant.parse(steps.get(i + 1).time())).toHours() : 6;
            Details now = s.data().instant().details();
            Next next = s.data().next1Hours() != null ? s.data().next1Hours() : s.data().next6Hours();
            Double precipitation = next == null || next.details() == null ? null : next.details().precipitationAmount();
            if (now.airTemperature() != null) {
                hours.add(new ForecastHour(time, now.airTemperature(), now.relativeHumidity(), precipitation, step));
            }
        }
        return hours;
    }

    private static Instant httpDate(String value) {
        Instant fallback = Instant.now().plus(Duration.ofMinutes(30));
        if (value == null) {
            return fallback;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    record Body(Properties properties) {
    }

    record Properties(List<Step> timeseries) {
    }

    record Step(String time, Data data) {
    }

    record Data(Now instant, @JsonProperty("next_1_hours") Next next1Hours,
                @JsonProperty("next_6_hours") Next next6Hours) {
    }

    record Now(Details details) {
    }

    record Next(Details details) {
    }

    record Details(@JsonProperty("air_temperature") Double airTemperature,
                   @JsonProperty("relative_humidity") Double relativeHumidity,
                   @JsonProperty("precipitation_amount") Double precipitationAmount) {
    }
}
