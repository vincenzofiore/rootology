package app.rootology.facts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CropCatalogTest {

    private final CropCatalog catalog = new CropCatalog();

    @Test
    void loadsTheApprovedCrops() {
        assertThat(catalog.all()).hasSize(66);
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

    @Test
    void aSentenceNamesACropWithItsArticle() {
        assertThat(catalog.all()).allSatisfy(c -> assertThat(Crop.ITALIAN_ARTICLES).contains(c.italianArticle()));
        assertThat(catalog.byId("strawberry").orElseThrow().italianInSentence()).isEqualTo("la fragola");
        assertThat(catalog.byId("garlic").orElseThrow().italianInSentence()).isEqualTo("l’aglio");
        assertThat(catalog.byId("spinach").orElseThrow().italianInSentence()).isEqualTo("lo spinacio");
        assertThat(catalog.byId("turnip-tops").orElseThrow().italianInSentence()).isEqualTo("le cime di rapa");
        assertThat(catalog.byId("tuscan-kale").orElseThrow().italianInSentence()).isEqualTo("il cavolo nero");
    }
}
