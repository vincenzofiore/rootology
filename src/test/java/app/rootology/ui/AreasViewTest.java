package app.rootology.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
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

    private static app.rootology.garden.GardenViews.PlotSummary sun(app.rootology.garden.DirectSun level,
                                                                     Integer summer, Integer winter) {
        return new app.rootology.garden.GardenViews.PlotSummary(1, 7, "Area",
                app.rootology.garden.PlotKind.POT, app.rootology.garden.Soil.POTTING_MIX, null, null, level,
                null, null, summer, winter, null, null);
    }
}
