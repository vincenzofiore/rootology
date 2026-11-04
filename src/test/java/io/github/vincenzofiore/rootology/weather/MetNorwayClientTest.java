package io.github.vincenzofiore.rootology.weather;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The MET Norway call against canned responses: coordinates, cache headers, parsing, failures. */
class MetNorwayClientTest {

    static final String BODY = """
            {"properties":{"meta":{"updated_at":"2026-11-04T09:17:28Z"},"timeseries":[
             {"time":"2026-11-04T10:00:00Z","data":{"instant":{"details":{"air_temperature":12.5,"relative_humidity":91.0}},
              "next_1_hours":{"details":{"precipitation_amount":0.4}}}},
             {"time":"2026-11-04T11:00:00Z","data":{"instant":{"details":{"air_temperature":13.0,"relative_humidity":88.0}},
              "next_1_hours":{"details":{"precipitation_amount":0.0}}}},
             {"time":"2026-11-04T17:00:00Z","data":{"instant":{"details":{"air_temperature":9.0}},
              "next_6_hours":{"details":{"precipitation_amount":1.2}}}}]}}
            """;

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://met.test")
            .defaultHeader(HttpHeaders.USER_AGENT, "rootology-test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final MetNorwayClient client = new MetNorwayClient(builder.build());

    @Test
    void parsesTheForecastWithItsSteps() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.EXPIRES, "Wed, 04 Nov 2026 10:01:06 GMT");
        headers.set(HttpHeaders.LAST_MODIFIED, "Wed, 04 Nov 2026 09:30:30 GMT");
        server.expect(requestTo("https://met.test/weatherapi/locationforecast/2.0/compact?lat=45.46&lon=9.19"))
                .andExpect(header(HttpHeaders.USER_AGENT, "rootology-test"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON).headers(headers));

        MetNorwayClient.Response response = client.fetch(45.4612, 9.1899, null);

        assertThat(response.hours()).containsExactly(
                new ForecastHour(Instant.parse("2026-11-04T10:00:00Z"), 12.5, 91.0, 0.4, 1),
                new ForecastHour(Instant.parse("2026-11-04T11:00:00Z"), 13.0, 88.0, 0.0, 6),
                new ForecastHour(Instant.parse("2026-11-04T17:00:00Z"), 9.0, null, 1.2, 6));
        assertThat(response.expires()).isEqualTo(Instant.parse("2026-11-04T10:01:06Z"));
        assertThat(response.lastModified()).isEqualTo("Wed, 04 Nov 2026 09:30:30 GMT");
    }

    @Test
    void sendsIfModifiedSinceAndAcceptsNotModified() {
        server.expect(requestTo("https://met.test/weatherapi/locationforecast/2.0/compact?lat=41.90&lon=12.50"))
                .andExpect(header(HttpHeaders.IF_MODIFIED_SINCE, "Wed, 04 Nov 2026 09:30:30 GMT"))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        MetNorwayClient.Response response = client.fetch(41.9, 12.5, "Wed, 04 Nov 2026 09:30:30 GMT");

        assertThat(response.notModified()).isTrue();
        assertThat(response.lastModified()).isEqualTo("Wed, 04 Nov 2026 09:30:30 GMT");
    }

    @Test
    void aServerErrorIsAnError() {
        server.expect(requestTo("https://met.test/weatherapi/locationforecast/2.0/compact?lat=41.90&lon=12.50"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetch(41.9, 12.5, null)).isInstanceOf(IllegalStateException.class);
    }
}
