package app.rootology.ui;

import app.rootology.facts.ClimateCells;
import app.rootology.facts.ClimateWindows;
import app.rootology.facts.ClimateWindows.ClimateWindow;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.CuratedKnowledge.WindowRow;
import app.rootology.facts.ReferenceSite;
import app.rootology.facts.SiteProfile;
import app.rootology.garden.GardenViews.GardenSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Month;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The harvest months of a crop growing now (#131): the month range of a window, in the page's language, and
 * the windows of the garden's own site — never a date estimated from the crop's cycle.
 */
class HarvestTextTest {

    private static final ClimateCells CELLS = new ClimateCells();
    private static final ClimateWindows WINDOWS = new ClimateWindows(new CropCatalog(), null);
    /** The texts of the test: the key itself, since only the months are checked here. */
    private static final NoteText.Texts TEXTS = (key, args) -> key;
    private static final Locale IT = Locale.ITALIAN;
    private static final Locale EN = Locale.ENGLISH;

    private static ClimateWindow window(int from, int to) {
        return new ClimateWindow(new WindowRow("tomato", "CENTRO", "HARVEST", from, to, "",
                "rootology-editorial", false), false);
    }

    @Test
    void aWindowIsItsTwoMonthsInThePagesLanguage() {
        assertThat(HarvestText.months(List.of(window(7, 9)), IT)).isEqualTo("lug–set");
        assertThat(HarvestText.months(List.of(window(7, 9)), EN)).isEqualTo("Jul–Sep");
        assertThat(HarvestText.fullMonths(List.of(window(7, 9)), IT)).isEqualTo("luglio–settembre");
        assertThat(HarvestText.fullMonths(List.of(window(7, 9)), EN)).isEqualTo("July–September");
        // One month is said once.
        assertThat(HarvestText.months(List.of(window(6, 6)), IT)).isEqualTo("giu");
    }

    @Test
    void aWindowOverTheNewYearKeepsBothMonths() {
        assertThat(HarvestText.months(List.of(window(10, 2)), IT)).isEqualTo("ott–feb");
        assertThat(HarvestText.months(List.of(window(10, 2)), EN)).isEqualTo("Oct–Feb");
        assertThat(HarvestText.fullMonths(List.of(window(11, 5)), IT)).isEqualTo("novembre–maggio");
    }

    @Test
    void aWindowOfTwelveMonthsIsTheWholeYear() {
        assertThat(HarvestText.allYear(window(1, 12))).isTrue();
        assertThat(HarvestText.allYear(window(3, 2))).isTrue();
        assertThat(HarvestText.allYear(window(10, 2))).isFalse();
        assertThat(HarvestText.value(List.of(window(1, 12)), TEXTS, IT)).hasValueSatisfying(value ->
                assertThat(value.getElement().getChild(1).getText()).isEqualTo("harvest.all.year.short"));
    }

    @Test
    void severalWindowsAreJoinedInCalendarOrder() {
        List<ClimateWindow> two = List.of(window(4, 5), window(11, 2));
        assertThat(HarvestText.months(two, IT)).isEqualTo("apr–mag, nov–feb");
    }

    @Test
    void noWindowSaysNothing() {
        assertThat(HarvestText.months(List.of(), IT)).isEmpty();
        assertThat(HarvestText.fullMonths(null, EN)).isEmpty();
        assertThat(HarvestText.value(List.of(), TEXTS, IT)).isEmpty();
    }

    @Test
    void aGrowingCropGetsTheHarvestWindowsOfTheGardensOwnPlace() {
        // The site is the garden's own cell, not a zone of the country: two gardens read two climates.
        SiteProfile vasto = site(42.11, 14.71, "IT");
        SiteProfile palermo = site(38.12, 13.36, "IT");
        assertThat(vasto.cell()).isEqualTo("42.1/14.7");
        assertThat(palermo.cell()).isEqualTo("38.1/13.4");

        // The chip's months are the site's HARVEST windows, as the facts give them to the questions.
        Map<String, List<ClimateWindow>> harvest = HarvestText.harvest(vasto, WINDOWS);
        assertThat(harvest).containsKey("tomato");
        // Only the harvest, never the sowing or the transplanting of the same crop.
        assertThat(harvest.get("tomato")).isNotEmpty()
                .allMatch(window -> HarvestText.ACTIVITY.equals(window.window().activity()));
        assertThat(HarvestText.months(harvest.get("tomato"), IT)).isEqualTo("lug–set");
        // Reading a site twice gives the same months, so the two pages agree.
        assertThat(HarvestText.months(HarvestText.harvest(vasto, WINDOWS).get("tomato"), IT))
                .isEqualTo(HarvestText.months(harvest.get("tomato"), IT));

        // Nothing is shown for a crop the site has no window for: no window, no guess.
        assertThat(harvest.get("wheat")).isNull();
        assertThat(HarvestText.value(harvest.get("wheat"), TEXTS, IT)).isEmpty();
    }

    @Test
    void aWinterCropKeepsTheWindowTheCuratedRowsDeclare() {
        // At the reference site the curated rows are the windows themselves: 10-2 reads "ott–feb".
        Map<String, List<ClimateWindow>> centro = HarvestText.harvest(ReferenceSite.CENTRO.profile(CELLS), WINDOWS);
        assertThat(HarvestText.months(centro.get("cauliflower"), IT)).isEqualTo("ott–feb");
        // A crop sown twice is harvested twice, and both windows are shown.
        assertThat(HarvestText.months(centro.get("spinach"), IT)).isEqualTo("apr–mag, nov–feb");
    }

    @Test
    void aGardenWithoutCoordinatesUsesTheSiteOfItsQuestions() {
        GardenSummary nowhere = new GardenSummary(1, "Orto", null, null, null, null, null, null);
        assertThat(HarvestText.site(nowhere, CELLS)).isPresent();
        assertThat(HarvestText.site(nowhere, CELLS).orElseThrow().zone()).isEqualTo("CENTRO");
        // A garden abroad with no coordinates has no profile, and its crops get no months.
        GardenSummary abroad = new GardenSummary(2, "Garden", null, null, null, "FR", null, null);
        assertThat(HarvestText.site(abroad, CELLS)).isEmpty();
        assertThat(HarvestText.site(null, CELLS)).isEmpty();
    }

    private static SiteProfile site(double latitude, double longitude, String country) {
        GardenSummary garden = new GardenSummary(1, "Orto", "Vasto", BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude), country, null, null);
        return HarvestText.site(garden, CELLS).orElseThrow();
    }

    /** The months the chips show are the ones a person reads, never an ISO month number (#131). */
    @Test
    void aMonthIsNeverShownAsANumber() {
        for (Month month : Month.values()) {
            assertThat(HarvestText.months(List.of(window(month.getValue(), month.getValue())), IT))
                    .isNotEqualTo(String.valueOf(month.getValue()));
        }
    }
}
