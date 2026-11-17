package app.rootology.pipeline;

import app.rootology.geocoding.Place;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The place rule of #107, without GeoNames: the exact name among the results, Italy first, the most populous
 * place when it has at least ten times the second's population, otherwise the person chooses.
 */
class PlaceLookupTest {

    private static Place place(String name, String countryCode, double latitude, double longitude, long population) {
        return new Place(name, null, countryCode, null, latitude, longitude, population);
    }

    @Test
    void onlyTheExactNameCountsNotTheNamesThatStartLikeIt() {
        var lookup = ContextResolver.choose(List.of(
                place("Vastogirardi", "IT", 41.77, 14.26, 200),
                place("Vasto", "IT", 42.11, 14.71, 40_000),
                place("Vastogirardi", "IT", 41.77, 14.26, 100)), "vasto");

        assertThat(lookup.outcome()).isEqualTo(ContextResolver.PlaceLookup.Outcome.FOUND);
        assertThat(lookup.place().name()).isEqualTo("Vasto");
        assertThat(lookup.place().elevationM()).as("the altitude is looked up when it is chosen").isNull();
    }

    @Test
    void theCaseAndTheAccentsOfTheNameDoNotMatter() {
        var lookup = ContextResolver.choose(List.of(place("Forlì", "IT", 44.22, 12.04, 117_000)), "FORLI");

        assertThat(lookup.outcome()).isEqualTo(ContextResolver.PlaceLookup.Outcome.FOUND);
        assertThat(lookup.place().name()).isEqualTo("Forlì");
    }

    @Test
    void italyComesFirstWhenTheSameNameIsElsewhere() {
        var lookup = ContextResolver.choose(List.of(
                place("Sant'Angelo", "CH", 46.00, 8.90, 900_000),
                place("Sant'Angelo", "IT", 42.09, 12.28, 5_000)), "Sant'Angelo");

        assertThat(lookup.outcome()).isEqualTo(ContextResolver.PlaceLookup.Outcome.FOUND);
        assertThat(lookup.place().countryCode()).isEqualTo("IT");
    }

    @Test
    void theMostPopulousPlaceWinsOnlyWhenItIsTenTimesTheNext() {
        var clear = ContextResolver.choose(List.of(
                place("Santa Maria", "IT", 40.00, 15.00, 50_000),
                place("Santa Maria", "IT", 45.00, 9.00, 5_000)), "Santa Maria");

        assertThat(clear.outcome()).isEqualTo(ContextResolver.PlaceLookup.Outcome.FOUND);
        assertThat(clear.place().latitude()).isEqualByComparingTo("40.0");

        var uncertain = ContextResolver.choose(List.of(
                place("Santa Maria", "IT", 40.00, 15.00, 50_000),
                place("Santa Maria", "IT", 45.00, 9.00, 6_000)), "Santa Maria");
        assertThat(uncertain.outcome()).as("five times is not enough").isEqualTo(
                ContextResolver.PlaceLookup.Outcome.AMBIGUOUS);
    }

    @Test
    void severalSimilarPlacesAreThePersonsChoiceMostPopulousFirstAndAtMostFive() {
        var lookup = ContextResolver.choose(List.of(
                place("Sant'Angelo", "IT", 42.09, 12.28, 5_000),
                place("Sant'Angelo", "IT", 45.52, 9.45, 6_000),
                place("Sant'Angelo", "IT", 43.00, 13.00, 7_000),
                place("Sant'Angelo", "IT", 44.00, 11.00, 8_000),
                place("Sant'Angelo", "IT", 41.00, 14.00, 9_000),
                place("Sant'Angelo", "IT", 40.00, 16.00, 10_000),
                place("Sant'Angelo", "IT", 39.00, 17.00, 11_000)), "Sant'Angelo");

        assertThat(lookup.outcome()).isEqualTo(ContextResolver.PlaceLookup.Outcome.AMBIGUOUS);
        assertThat(lookup.candidates()).hasSize(5);
        assertThat(lookup.candidates().getFirst().latitude()).isEqualByComparingTo("39.0");
    }

    @Test
    void withoutTheExactNameThePlacesThatStartLikeItAreThePersonsChoiceItalyFirst() {
        var lookup = ContextResolver.choose(List.of(
                place("Sant'Angelo Lodigiano", "IT", 45.24, 9.41, 13_000),
                place("Sant'Angelo in Vado", "IT", 43.67, 12.42, 4_000),
                place("Sant'Angelo Town", "US", 30.00, -90.00, 90_000)), "Sant’Angelo");

        assertThat(lookup.outcome()).isEqualTo(ContextResolver.PlaceLookup.Outcome.AMBIGUOUS);
        assertThat(lookup.candidates()).extracting(QuestionContext.Choice.Place::name).containsExactly(
                "Sant'Angelo Lodigiano", "Sant'Angelo in Vado", "Sant'Angelo Town");
    }

    @Test
    void theTypographicApostropheIsThePlainOne() {
        var lookup = ContextResolver.choose(List.of(place("Sant'Angelo", "IT", 42.09, 12.28, 5_000)), "Sant’Angelo");

        assertThat(lookup.outcome()).isEqualTo(ContextResolver.PlaceLookup.Outcome.FOUND);
    }

    @Test
    void aNameGeoNamesDoesNotKnowIsNotFound() {
        assertThat(ContextResolver.choose(List.of(), "Xyzville").outcome())
                .isEqualTo(ContextResolver.PlaceLookup.Outcome.NOT_FOUND);
    }

    @Test
    void aFailingGeoNamesGivesNoResultAndSoNoPlace() {
        // A failing or disabled GeoNames returns no place at all, which is the same as a name it does not know.
        var lookup = ContextResolver.choose(List.of(), "Torino");

        assertThat(lookup.outcome()).isEqualTo(ContextResolver.PlaceLookup.Outcome.NOT_FOUND);
        assertThat(lookup.place()).isNull();
        assertThat(lookup.candidates()).isEmpty();
    }
}
