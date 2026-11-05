package io.github.vincenzofiore.rootology.geocoding;

import io.github.vincenzofiore.rootology.resilience.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** GeoNames calls against canned answers: parsing, cache, errors in the body, the breaker. */
class GeoNamesTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://geo.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final CircuitBreaker breaker = new CircuitBreaker("test GeoNames");
    private final GeoNames geoNames = new GeoNames(builder.build(), "rootology-test", breaker);

    private static final String SEARCH = "https://geo.test/searchJSON?name_startsWith=Bolo&featureClass=P&maxRows=8"
            + "&orderby=relevance&lang=it&style=MEDIUM&username=rootology-test";

    @Test
    void findsPlacesAndCachesThem() {
        server.expect(ExpectedCount.once(), requestTo(SEARCH)).andRespond(withSuccess("""
                {"totalResultsCount":2,"geonames":[
                 {"name":"Bologna","adminName1":"Emilia-Romagna","countryCode":"IT","countryName":"Italia",
                  "lat":"44.49381","lng":"11.33875","population":366133},
                 {"name":"Boloña","adminName1":"Lima","countryCode":"PE","countryName":"Perù","lat":"-12.1","lng":"-77.0"}]}
                """, MediaType.APPLICATION_JSON));

        var first = geoNames.search("Bolo", "it");
        var again = geoNames.search(" bolo ", "IT");

        assertThat(first).extracting(Place::label)
                .containsExactly("Bologna, Emilia-Romagna, Italia", "Boloña, Lima, Perù");
        assertThat(first.getFirst().latitude()).isEqualTo(44.49381);
        assertThat(again).isEqualTo(first);
        server.verify();
    }

    @Test
    void shortTextOrNoAccountMeansNoSearch() {
        assertThat(geoNames.search("Bo", "it")).isEmpty();
        assertThat(new GeoNames(builder.build(), " ", breaker).search("Bologna", "it")).isEmpty();
        assertThat(new GeoNames(builder.build(), "", breaker).enabled()).isFalse();
        server.verify(); // no request at all
    }

    @Test
    void anErrorInTheBodyOrAServerErrorGivesNothingAndCountsAsAFailure() {
        server.expect(requestTo(SEARCH)).andRespond(withSuccess("""
                {"status":{"message":"user account not enabled to use the free webservice.","value":10}}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEARCH)).andRespond(withServerError());

        assertThat(geoNames.search("Bolo", "it")).isEmpty();
        assertThat(geoNames.search("Bolo", "it")).as("failures are not cached").isEmpty();
        assertThat(breaker.failing()).isTrue();
        server.verify();
    }

    @Test
    void theNearestTownToAPositionAndNoneAtSea() {
        server.expect(requestTo("https://geo.test/findNearbyPlaceNameJSON?lat=45.46&lng=9.19&lang=it&style=MEDIUM"
                        + "&username=rootology-test"))
                .andRespond(withSuccess("""
                        {"geonames":[{"name":"Milano","adminName1":"Lombardia","countryCode":"IT",
                          "countryName":"Italia","lat":"45.46427","lng":"9.18951","distance":"0.4"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://geo.test/findNearbyPlaceNameJSON?lat=40.00&lng=12.00&lang=it&style=MEDIUM"
                        + "&username=rootology-test"))
                .andRespond(withSuccess("""
                        {"geonames":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(geoNames.nearestPlace(45.4612, 9.1899, "it"))
                .contains(new Place("Milano", "Lombardia", "IT", "Italia", 45.4612, 9.1899));
        assertThat(geoNames.nearestPlace(40.0, 12.0, "it")).isEmpty();
        assertThat(breaker.failing()).as("nothing nearby is not a failure").isFalse();
        server.verify();
    }
}
