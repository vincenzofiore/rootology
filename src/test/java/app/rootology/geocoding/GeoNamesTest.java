package app.rootology.geocoding;

import app.rootology.resilience.CircuitBreaker;
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
            + "&orderby=relevance&lang=it&style=FULL&username=rootology-test";

    @Test
    void findsPlacesAndCachesThem() {
        server.expect(ExpectedCount.once(), requestTo(SEARCH)).andRespond(withSuccess("""
                {"totalResultsCount":2,"geonames":[
                 {"name":"Bologna","toponymName":"Bologna","alternateNames":[{"name":"Bologna","lang":"it","isPreferredName":true}],"adminName1":"Emilia-Romagna","adminCode1":"08","countryCode":"IT","countryName":"Italia",
                  "lat":"44.49381","lng":"11.33875","population":366133},
                 {"name":"Boloña","toponymName":"Boloña","adminName1":"Lima","adminCode1":"15","countryCode":"PE","countryName":"Perù","lat":"-12.1","lng":"-77.0"}]}
                """, MediaType.APPLICATION_JSON));

        var first = geoNames.search("Bolo", "it");
        var again = geoNames.search(" bolo ", "IT");

        assertThat(first).extracting(Place::label)
                .containsExactly("Bologna, Emilia-Romagna", "Boloña, Lima, Perù");
        assertThat(first.getFirst().latitude()).isEqualTo(44.49381);
        assertThat(again).isEqualTo(first);
        server.verify();
    }

    @Test
    void theTypographicApostropheIsSearchedAsThePlainOne() {
        server.expect(ExpectedCount.once(), org.springframework.test.web.client.match.MockRestRequestMatchers
                        .queryParam("name_startsWith", "Sant'Angelo"))
                .andRespond(withSuccess("""
                        {"totalResultsCount":1,"geonames":[
                         {"name":"Sant'Angelo Lodigiano","toponymName":"Sant'Angelo Lodigiano","adminName1":"Lombardia","adminCode1":"09","countryCode":"IT",
                          "countryName":"Italia","lat":"45.23","lng":"9.40","population":13000}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(geoNames.search("Sant’Angelo", "it")).extracting(Place::name).containsExactly("Sant'Angelo Lodigiano");
        assertThat(geoNames.search("Sant'Angelo", "it")).hasSize(1);
        server.verify();
    }

    @Test
    void shortTextOrNoAccountMeansNoSearch() {
        assertThat(geoNames.search("B", "it")).isEmpty();
        assertThat(new GeoNames(builder.build(), " ", breaker).search("Bologna", "it")).isEmpty();
        assertThat(new GeoNames(builder.build(), "", breaker).enabled()).isFalse();
        server.verify(); // no request at all
    }

    @Test
    void twoLettersFindTheTownNamedSoFirstItalyBeforeAbroadAndEachLineOnce() {
        // Ro (Ferrara): by its start alone, Rome and Rovigo would fill the list (#87); two villages of one
        // Belgian region read the same, so one line is enough.
        server.expect(ExpectedCount.once(), requestTo("https://geo.test/searchJSON?name_equals=Ro&featureClass=P"
                + "&maxRows=8&orderby=relevance&lang=it&style=FULL&username=rootology-test"))
                .andRespond(withSuccess("""
                        {"geonames":[
                         {"name":"Ro","toponymName":"Ro","adminName1":"Vallonia","adminCode1":"11","countryCode":"BE","countryName":"Belgio",
                          "lat":"50.10","lng":"4.80"},
                         {"name":"Ro","toponymName":"Ro","adminName1":"Vallonia","adminCode1":"11","countryCode":"BE","countryName":"Belgio",
                          "lat":"50.20","lng":"4.90"},
                         {"name":"Ro","toponymName":"Ro","adminName1":"Emilia-Romagna","adminCode1":"08","countryCode":"IT",
                          "countryName":"Italia","lat":"44.94","lng":"11.76"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo("https://geo.test/searchJSON?name_startsWith=Ro"
                + "&featureClass=P&maxRows=8&orderby=relevance&lang=it&style=FULL&username=rootology-test"))
                .andRespond(withSuccess("""
                        {"geonames":[
                         {"name":"Roma","toponymName":"Roma","adminName1":"Lazio","adminCode1":"07","countryCode":"IT","countryName":"Italia",
                          "lat":"41.89","lng":"12.48"},
                         {"name":"Ro","toponymName":"Ro","adminName1":"Emilia-Romagna","adminCode1":"08","countryCode":"IT","countryName":"Italia",
                          "lat":"44.94","lng":"11.76"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(geoNames.search("Ro", "it")).extracting(Place::label)
                .containsExactly("Ro, Emilia-Romagna", "Ro, Vallonia, Belgio", "Roma, Lazio");
        server.verify();
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
    void theElevationOfAPositionAndTheMissingValue() {
        server.expect(requestTo("https://geo.test/gtopo30JSON?lat=45.46&lng=9.19&username=rootology-test"))
                .andRespond(withSuccess("{\"srtm3\":120,\"gtopo30\":122}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://geo.test/gtopo30JSON?lat=0.00&lng=0.00&username=rootology-test"))
                .andRespond(withSuccess("{\"gtopo30\":-9999}", MediaType.APPLICATION_JSON));

        assertThat(geoNames.elevation(45.4612, 9.1899)).contains(122);
        assertThat(geoNames.elevation(0.0, 0.0)).as("-9999 is no value").isEmpty();
        server.verify();
    }

    @Test
    void theNearestTownToAPositionAndNoneAtSea() {
        server.expect(requestTo("https://geo.test/findNearbyPlaceNameJSON?lat=45.46&lng=9.19&lang=it&style=MEDIUM"
                        + "&username=rootology-test"))
                .andRespond(withSuccess("""
                        {"geonames":[{"name":"Milano","adminName1":"Lombardia","adminCode1":"09","countryCode":"IT",
                          "countryName":"Italia","lat":"45.46427","lng":"9.18951","distance":"0.4"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://geo.test/findNearbyPlaceNameJSON?lat=40.00&lng=12.00&lang=it&style=MEDIUM"
                        + "&username=rootology-test"))
                .andRespond(withSuccess("""
                        {"geonames":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(geoNames.nearestPlace(45.4612, 9.1899, "it"))
                .contains(new Place("Milano", "Lombardia", "IT", "Italia", 45.4612, 9.1899, 0, "09"));
        assertThat(geoNames.nearestPlace(40.0, 12.0, "it")).isEmpty();
        assertThat(breaker.failing()).as("nothing nearby is not a failure").isFalse();
        server.verify();
    }

    @Test
    void thePlaceIsNamedInTheSearchLanguageNotByTheNameThatMatchedTheText() {
        // "lion" matches Lyon's old English name, "liong" one of Riom's: the place keeps its own name (#184).
        server.expect(ExpectedCount.once(), requestTo("https://geo.test/searchJSON?name_startsWith=lion"
                + "&featureClass=P&maxRows=8&orderby=relevance&lang=en&style=FULL&username=rootology-test"))
                .andRespond(withSuccess("""
                        {"geonames":[
                         {"name":"Lion","toponymName":"Lyon","alternateNames":[{"name":"Lion","lang":"en"},
                          {"name":"Lyon","lang":"en","isPreferredName":true}],"adminName1":"Auvergne-Rhône-Alpes",
                          "adminCode1":"84","countryCode":"FR","countryName":"France","lat":"45.75","lng":"4.85",
                          "population":522250},
                         {"name":"liong","toponymName":"Riom","alternateNames":[{"name":"Riom","lang":"en"}],
                          "adminName1":"Auvergne-Rhône-Alpes","adminCode1":"84","countryCode":"FR",
                          "countryName":"France","lat":"45.89","lng":"3.11"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(geoNames.search("lion", "en")).extracting(Place::label)
                .containsExactly("Lyon, Auvergne-Rhône-Alpes, France", "Riom, Auvergne-Rhône-Alpes, France");
        server.verify();
    }

    @Test
    void theItalianNameOfAFrenchTownAndTheMainNameWhenTheLanguageHasNone() {
        server.expect(ExpectedCount.once(), requestTo("https://geo.test/searchJSON?name_startsWith=lione"
                + "&featureClass=P&maxRows=8&orderby=relevance&lang=it&style=FULL&username=rootology-test"))
                .andRespond(withSuccess("""
                        {"geonames":[
                         {"name":"Lione","toponymName":"Lyon","alternateNames":[{"name":"Lione","lang":"it"},
                          {"name":"Lyon","lang":"en","isPreferredName":true}],"adminName1":"Auvergne-Rhône-Alpes",
                          "adminCode1":"84","countryCode":"FR","countryName":"Francia","lat":"45.75","lng":"4.85"}]}
                        """, MediaType.APPLICATION_JSON));
        // Nablus matched "napouse", an old form, and has no Italian name: its main name is the only one (#184).
        server.expect(ExpectedCount.once(), requestTo("https://geo.test/searchJSON?name_startsWith=napo"
                + "&featureClass=P&maxRows=8&orderby=relevance&lang=it&style=FULL&username=rootology-test"))
                .andRespond(withSuccess("""
                        {"geonames":[
                         {"name":"Napoli","toponymName":"Naples","alternateNames":[{"name":"Napoli","lang":"it",
                          "isPreferredName":true},{"name":"Naples","lang":"en","isPreferredName":true}],
                          "adminName1":"Campania","adminCode1":"04","countryCode":"IT","countryName":"Italia",
                          "lat":"40.85","lng":"14.27","population":959574},
                         {"name":"Napouse","toponymName":"Nablus","alternateNames":[{"name":"Nablus","lang":"en"},
                          {"name":"نابلس","lang":"ar"}],"adminName1":"West Bank","countryCode":"PS",
                          "countryName":"Palestina","lat":"32.22","lng":"35.25"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(geoNames.search("lione", "it")).extracting(Place::name).containsExactly("Lione");
        assertThat(geoNames.search("napo", "it")).extracting(Place::name).containsExactly("Napoli", "Nablus");
        server.verify();
    }

    @Test
    void thePreferredAlternateNameWinsAndAShortOneOrAnotherLanguageIsSkipped() {
        server.expect(ExpectedCount.once(), requestTo("https://geo.test/searchJSON?name_startsWith=naples"
                + "&featureClass=P&maxRows=8&orderby=relevance&lang=en&style=FULL&username=rootology-test"))
                .andRespond(withSuccess("""
                        {"geonames":[
                         {"name":"Napule","toponymName":"Naples","alternateNames":[
                          {"name":"NAP","lang":"en","isShortName":true},
                          {"name":"Nablus","lang":"link"},
                          {"name":"Naples","lang":"en","isPreferredName":true},
                          {"name":"Gulf of Naples","lang":"en"}],
                          "adminName1":"Campania","adminCode1":"04","countryCode":"IT","countryName":"Italy",
                          "lat":"40.85","lng":"14.27"},
                         {"name":"napoule","toponymName":"Napoule","alternateNames":[
                          {"name":"NPL","lang":"en","isShortName":true},
                          {"name":"Napoule","lang":"en"}],
                          "adminName1":"Provence","countryCode":"FR","countryName":"France","lat":"43.52","lng":"6.94"},
                         {"name":"Napoule-sur-Mer","toponymName":"Théoule-sur-Mer","alternateNames":[
                          {"name":"NPLM","lang":"en","isShortName":true}],
                          "adminName1":"Provence","countryCode":"FR","countryName":"France","lat":"43.51","lng":"6.94"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(geoNames.search("naples", "en")).extracting(Place::name)
                .as("preferred first, then the first long name, then the main name")
                .containsExactly("Naples", "Napoule", "Théoule-sur-Mer");
        server.verify();
    }
}
