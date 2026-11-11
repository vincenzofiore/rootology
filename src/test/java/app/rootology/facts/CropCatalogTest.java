package app.rootology.facts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CropCatalogTest {

    private final CropCatalog catalog = new CropCatalog();

    @Test
    void loadsTheApprovedListOf39Crops() {
        assertThat(catalog.all()).hasSize(39);
        assertThat(catalog.byId("tomato")).hasValueSatisfying(c -> {
            assertThat(c.italianName()).isEqualTo("Pomodoro");
            assertThat(c.family()).isEqualTo("Solanaceae");
            assertThat(c.wikidataId()).isEqualTo("Q23501");
            assertThat(c.ecocropId()).isEqualTo("1379");
        });
    }

    @Test
    void everyCropHasNamesAndAFamily() {
        assertThat(catalog.all()).allSatisfy(c -> {
            assertThat(c.id()).matches("[a-z-]+");
            assertThat(c.italianName()).isNotBlank();
            assertThat(c.englishName()).isNotBlank();
            assertThat(c.scientificName()).isNotBlank();
            assertThat(c.family()).endsWith("aceae");
        });
    }
}
