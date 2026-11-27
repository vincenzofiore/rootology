package app.rootology.ui;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import app.rootology.facts.DepthClass;
import app.rootology.garden.ContainerDepthRule;
import app.rootology.garden.CropProposalRule.CropFacts;
import app.rootology.garden.CropProposalRule.Window;
import app.rootology.garden.CropProposalService.CropChecks;
import app.rootology.garden.CropProposalService.Light;
import app.rootology.garden.DirectSun;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.RotationRule;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** The areas of #85: the five kinds and their drawings, the old route, and the day of a harvest. */
class AreasViewTest {

    @Test
    void theAreasPageIsTheOldPlotsPageOfTheSameGarden() {
        assertThat(AreasView.class.getAnnotation(com.vaadin.flow.router.Route.class).value())
                .isEqualTo("spaces/:spaceId/gardens/:gardenId/areas");
        assertThat(PlotsRedirectView.class.getAnnotation(com.vaadin.flow.router.Route.class).value())
                .isEqualTo("spaces/:spaceId/gardens/:gardenId/plots");
        assertThat(PlotsRedirectView.target("3", "5")).isEqualTo("/spaces/3/gardens/5/areas");
    }

    @Test
    void everyKindHasItsOwnIcon() {
        assertThat(app.rootology.garden.PlotKind.values()).hasSize(5);
        assertThat(java.util.Arrays.stream(app.rootology.garden.PlotKind.values())
                .map(AreaKindIcons::of).toList())
                .doesNotHaveDuplicates()
                .allSatisfy(path -> assertThat(getClass().getResource("/META-INF/resources/" + path))
                        .as(path).isNotNull());
    }

    @Test
    void everyKindHasANameInBothLanguages() throws IOException {
        Properties it = TranslationsTest.load("messages.properties");
        Properties en = TranslationsTest.load("messages_en.properties");
        for (app.rootology.garden.PlotKind kind : app.rootology.garden.PlotKind.values()) {
            assertThat(it.getProperty("plotkind." + kind.name())).as(kind.name()).isNotBlank();
            assertThat(en.getProperty("plotkind." + kind.name())).as(kind.name()).isNotBlank();
        }
        assertThat(it.getProperty("plotkind.POT")).isEqualTo("vaso");
        assertThat(it.getProperty("plotkind.RAISED_BED")).isEqualTo("aiuola rialzata");
    }

    @Test
    void aCardTellsWhatGrowsNowFromWhatGrewBeforeTheNewestFirst() {
        LocalDate spring = LocalDate.of(2026, 3, 1);
        LocalDate summer = LocalDate.of(2026, 6, 1);
        app.rootology.garden.GardenViews.PlantingSummary peas = planting(1, "pea", spring,
                LocalDate.of(2026, 5, 20));
        app.rootology.garden.GardenViews.PlantingSummary basil = planting(2, "basil", summer, null);
        app.rootology.garden.GardenViews.PlantingSummary beans = planting(3, "bean", LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 7, 2));
        List<app.rootology.garden.GardenViews.PlantingSummary> all = List.of(peas, basil, beans);

        assertThat(AreasView.current(all)).extracting(p -> p.cropId()).containsExactly("basil");
        assertThat(AreasView.past(all)).extracting(p -> p.cropId()).containsExactly("bean", "pea");
        assertThat(AreasView.current(AreasView.past(all))).as("a past planting is never also current").isEmpty();
        assertThat(AreasView.past(List.of(basil))).as("an area with nothing grown before has no list").isEmpty();
    }

    private static app.rootology.garden.GardenViews.PlantingSummary planting(long id, String cropId,
                                                                           LocalDate plantedOn, LocalDate endedOn) {
        return new app.rootology.garden.GardenViews.PlantingSummary(id, 7, "Area",
                app.rootology.garden.PlotKind.GROUND, cropId, cropId,
                app.rootology.garden.PlantingMethod.SOWN, plantedOn, endedOn, null);
    }

    @Test
    void aHarvestIsTodayAtTheLatestAndNeverBeforeThePlanting() {
        LocalDate today = LocalDate.of(2026, 7, 10);
        LocalDate planted = LocalDate.of(2026, 4, 1);
        assertThat(AreasView.harvestDateValid(planted, planted, today)).as("harvested the day it was planted").isTrue();
        assertThat(AreasView.harvestDateValid(today, planted, today)).isTrue();
        assertThat(AreasView.harvestDateValid(today.plusDays(1), planted, today)).as("in the future").isFalse();
        assertThat(AreasView.harvestDateValid(planted.minusDays(1), planted, today)).as("before the planting").isFalse();
        assertThat(AreasView.harvestDateValid(null, planted, today)).isFalse();
    }

    private static NoteText.Texts italian() {
        Translations translations = new Translations();
        return (key, args) -> translations.getTranslation(key, java.util.Locale.ITALIAN, args);
    }

    @Test
    void theTooltipOfTheSunSaysTheHoursAndWhoDeclaredThem() {
        // #89: the icon's tooltip and aria label carry the same sentence, the seasons given only.
        assertThat(AreasView.sunTooltip("mezz’ombra", 7, 2, null, italian()))
                .isEqualTo("Sole diretto: 7 ore d’estate, 2 ore d’inverno");
        assertThat(AreasView.sunTooltip("mezz’ombra", 7, null, null, italian()))
                .isEqualTo("Sole diretto: 7 ore d’estate");
        assertThat(AreasView.sunTooltip("mezz’ombra", null, 2, null, italian()))
                .isEqualTo("Sole diretto: 2 ore d’inverno");
        // Without hours the level is what the icon says, as before #89.
        assertThat(AreasView.sunTooltip("ombra", null, null, null, italian())).isEqualTo("ombra");
        // Who declared it and when is added when it is known.
        assertThat(AreasView.sunTooltip("ombra", null, null, "Dichiarato da Ada il 3 mag, 14:05", italian()))
                .isEqualTo("ombra · Dichiarato da Ada il 3 mag, 14:05");
        assertThat(AreasView.sunTooltip("ombra", null, null, "  ", italian())).as("nobody known").isEqualTo("ombra");
        assertThat(AreasView.sunTooltip(null, null, null, null, italian())).as("no level, no fact").isNull();
    }

    @Test
    void theSeasonOfTheMonthChoosesTheLevel() {
        // #89: the hours of that season when given, else the declared level; the other season is not guessed.
        app.rootology.garden.GardenViews.PlotSummary hours = sun(null, 7, 2);
        assertThat(hours.directSunIn(java.time.Month.JUNE)).isEqualTo(app.rootology.garden.DirectSun.FULL_SUN);
        assertThat(hours.directSunIn(java.time.Month.JANUARY)).isEqualTo(app.rootology.garden.DirectSun.SHADE);
        assertThat(hours.directSunIn(java.time.Month.APRIL)).isEqualTo(app.rootology.garden.DirectSun.FULL_SUN);
        assertThat(hours.directSunIn(java.time.Month.OCTOBER)).isEqualTo(app.rootology.garden.DirectSun.SHADE);

        app.rootology.garden.GardenViews.PlotSummary declared = sun(app.rootology.garden.DirectSun.PART_SHADE,
                7, null);
        assertThat(declared.directSunIn(java.time.Month.JULY)).as("the summer hours win over the level")
                .isEqualTo(app.rootology.garden.DirectSun.FULL_SUN);
        assertThat(declared.directSunIn(java.time.Month.DECEMBER)).as("no winter hours, the declared level")
                .isEqualTo(app.rootology.garden.DirectSun.PART_SHADE);

        app.rootology.garden.GardenViews.PlotSummary none = sun(null, null, null);
        assertThat(none.directSunIn(java.time.Month.JULY)).as("nothing declared").isNull();
    }


    @Test
    void aChosenCropShowsItsCategoryAndOneIconPerCheckWithItsSentence() {
        // #185: the category names the family and the use, then one icon per check that applies, good, warning
        // or unknown, each with its sentence as tooltip and as what a screen reader says.
        CropChecks checks = new CropChecks(
                facts("tomato", "Solanaceae", false,
                        List.of(new Window("TRANSPLANT", 4, true, true, List.of("rootology-editorial"))),
                        true, true),
                List.of("basil"), List.of("potato"),
                new RotationRule.RotationWarning(RotationRule.ID, "Solanaceae",
                        new PlantingSummary(1, 7, "Area", app.rootology.garden.PlotKind.GROUND, "potato",
                                "patata", PlantingMethod.SOWN, LocalDate.of(2025, 3, 1),
                                LocalDate.of(2025, 8, 1), null),
                        "tomato", "reason"),
                Light.TOO_DARK);

        Div row = PlantingChecks.of(checks, italian(), AreasViewTest::cropName);

        assertThat(row.getChildren().map(Component::getElement).map(e -> e.getText()).toList())
                .as("the category first").anyMatch(text -> text.contains("Solanaceae · commestibile"));
        assertThat(check(row, "rootology-planting-check-good"))
                .containsExactly("Buona vicina: basilico cresce già qui.");
        assertThat(check(row, "rootology-planting-check-warning"))
                .containsExactly("Antagonista: patata cresce già qui.",
                        "Stessa famiglia di patata, la coltura precedente (Solanaceae).",
                        "Sole diretto insufficiente: pomodoro ne chiede più di quanto prenda l’area.");
        assertThat(check(row, "rootology-planting-check-unknown")).isEmpty();
    }

    @Test
    void aContainerTooShallowShowsTheWarningWithItsAriaLabel() {
        // #186: container-depth-v1 says the crop asks for more soil than this pot has.
        CropChecks shallow = new CropChecks(facts("tomato", "Solanaceae", false,
                List.of(new Window("TRANSPLANT", 4, true, true, List.of("rootology-editorial"))), false, false),
                List.of(), List.of(), null, Light.ENOUGH,
                new ContainerDepthRule.Check(ContainerDepthRule.Verdict.TOO_SHALLOW, DepthClass.DEEP, 20));

        Div row = PlantingChecks.of(shallow, italian(), AreasViewTest::cropName);

        assertThat(check(row, "rootology-planting-check-warning"))
                .containsExactly("Terriccio troppo basso: pomodoro chiede almeno 30 cm, qui ce ne sono 20.");
        Span warning = row.getChildren().filter(Span.class::isInstance).map(Span.class::cast)
                .filter(span -> span.getClassNames().contains("rootology-planting-check-warning"))
                .findFirst().orElseThrow();
        assertThat(warning.getChildren().filter(Span.class::isInstance).map(Span.class::cast)
                .map(Span::getText).toList()).as("the short word, visible on a phone")
                .containsExactly("Vaso basso");
    }

    @Test
    void aDepthTheRulesDoNotKnowKeepsTheDialogQuiet() {
        // #186: unknown shows nothing in the planting dialog, unlike the light's "?".
        CropChecks unknown = new CropChecks(facts("carrot", "Apiaceae", false,
                List.of(new Window("SOW", 4, true, true, List.of("rootology-editorial"))), false, false),
                List.of(), List.of(), null, Light.ENOUGH);
        assertThat(unknown.depth().verdict()).isEqualTo(ContainerDepthRule.Verdict.UNKNOWN);

        Div row = PlantingChecks.of(unknown, italian(), AreasViewTest::cropName);

        assertThat(row.getChildren().map(Component::getElement).map(e -> e.getText()).toList())
                .containsExactly("Apiaceae · commestibile");
        assertThat(check(row, "rootology-planting-check")).isEmpty();
    }

    @Test
    void aCropWithNothingToSayShowsOnlyItsCategory() {
        CropChecks checks = new CropChecks(facts("carrot", "Apiaceae", false,
                List.of(new Window("SOW", 4, true, true, List.of("rootology-editorial"))), false, false),
                List.of(), List.of(), null, Light.ENOUGH);

        Div row = PlantingChecks.of(checks, italian(), AreasViewTest::cropName);

        assertThat(row.getChildren().map(Component::getElement).map(e -> e.getText()).toList())
                .containsExactly("Apiaceae · commestibile");
        assertThat(check(row, "rootology-planting-check")).isEmpty();
    }

    private static CropFacts facts(String cropId, String family, boolean service, List<Window> windows,
                                   boolean rotation, boolean antagonist) {
        return new CropFacts(cropId, family, service, 60.0, DirectSun.PART_SHADE, windows, rotation, antagonist,
                false, false, List.of(), false, null);
    }

    /** The crops of the checks of this test, by their Italian names. */
    private static String cropName(String cropId) {
        return Map.of("tomato", "pomodoro", "basil", "basilico", "potato", "patata", "carrot", "carota")
                .getOrDefault(cropId, cropId);
    }

    /** The sentences of the checks of one tone, from their tooltip and their aria label. */
    private static List<String> check(Div row, String tone) {
        return row.getChildren().filter(Span.class::isInstance).map(Span.class::cast)
                .filter(span -> span.getClassNames().contains(tone))
                .map(span -> {
                    String sentence = String.valueOf(span.getElement().getAttribute("aria-label"));
                    assertThat(span.getElement().getAttribute("title")).as("the same tooltip").isEqualTo(sentence);
                    assertThat(span.getElement().getAttribute("role")).isEqualTo("img");
                    return sentence;
                }).toList();
    }

    private static app.rootology.garden.GardenViews.PlotSummary sun(app.rootology.garden.DirectSun level,
                                                                     Integer summer, Integer winter) {
        return new app.rootology.garden.GardenViews.PlotSummary(1, 7, "Area",
                app.rootology.garden.PlotKind.POT, app.rootology.garden.Soil.POTTING_MIX, null, null, level,
                null, null, summer, winter, null, null);
    }
}
