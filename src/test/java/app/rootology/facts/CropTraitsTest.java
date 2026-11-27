package app.rootology.facts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The curated crop traits (#141): ECOCROP's cycle and light, the antagonists, the service plants. */
class CropTraitsTest {

    private final CropTraits traits = new CropTraits(new CropCatalog());

    @Test
    void ecoCropGivesTheCycleAndTheLightOptimum() {
        assertThat(traits.cycleMinDays("tomato")).contains(70.0);
        assertThat(traits.lightOptimum("tomato")).contains("clear skies");
        assertThat(traits.lightOptimum("chard")).contains("very bright");
    }

    @Test
    void aCropWithoutAnEcocropRecordHasNoTrait() {
        assertThat(traits.cycleMinDays("sweet-alyssum")).isEmpty();
        assertThat(traits.lightOptimum("sweet-alyssum")).isEmpty();
        assertThat(traits.cycleMinDays("no-such-crop")).isEmpty();
    }

    @Test
    void noCropHasAContainerDepthYet() {
        // #186: container-depths.csv has only its header and a comment until CSU allows its values.
        assertThat(traits.depthClass("tomato")).isEmpty();
        assertThat(traits.depthClass("lettuce")).isEmpty();
        assertThat(traits.depthClass("no-such-crop")).isEmpty();
    }

    @Test
    void antagonistsAreReadInEitherDirection() {
        assertThat(traits.antagonists("tomato", "potato")).isTrue();
        assertThat(traits.antagonists("potato", "tomato")).isTrue();
        assertThat(traits.antagonists("tomato", "basil")).isFalse();
        assertThat(traits.antagonists("no-such-crop", "tomato")).isFalse();
    }

    @Test
    void aServicePlantRowNamesItsAgentsAndTheirSources() {
        CropTraits.Beneficial alyssum = traits.beneficial("sweet-alyssum").orElseThrow();

        assertThat(alyssum.traditionalOnly()).isFalse();
        assertThat(alyssum.sources()).contains("ucipm-diamondback-moth", "rootology-editorial");
        assertThat(traits.beneficial("phacelia")).isPresent();
    }

    @Test
    void aServicePlantRowWithTraditionalEvidenceOnlySaysSo() {
        CropTraits.Beneficial mustard = traits.beneficial("white-mustard").orElseThrow();

        assertThat(mustard.traditionalOnly()).isTrue();
        assertThat(mustard.sources()).contains("rootology-editorial");
        assertThat(traits.beneficial("basil")).isEmpty();
    }
}