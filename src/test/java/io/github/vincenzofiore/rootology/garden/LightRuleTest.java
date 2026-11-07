package io.github.vincenzofiore.rootology.garden;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The mapping light-need-v1 uses (#54): ECOCROP's light optimum to the least light a plot must get. */
class LightRuleTest {

    @Test
    void ecocropLightOptimaMapToTheLeastDirectSunThatSatisfiesThem() {
        assertThat(LightRule.needs("very bright")).contains(DirectSun.FULL_SUN);
        assertThat(LightRule.needs("clear skies")).contains(DirectSun.PART_SHADE);
        assertThat(LightRule.needs("cloudy skies")).as("no vegetable grows well in full shade")
                .contains(DirectSun.PART_SHADE);
    }

    @Test
    void theMappingIgnoresCaseAndSpacesButGuessesNothingElse() {
        assertThat(LightRule.needs(" Very Bright ")).contains(DirectSun.FULL_SUN);
        assertThat(LightRule.needs("semi-shade")).isEmpty();
        assertThat(LightRule.needs("")).isEmpty();
        assertThat(LightRule.needs(null)).isEmpty();
    }

    @Test
    void aPlotIsTooDarkOnlyWhenItGivesLessLightThanTheCropNeeds() {
        assertThat(LightRule.tooDark(DirectSun.SHADE, DirectSun.FULL_SUN)).isTrue();
        assertThat(LightRule.tooDark(DirectSun.SHADE, DirectSun.PART_SHADE)).isTrue();
        assertThat(LightRule.tooDark(DirectSun.PART_SHADE, DirectSun.FULL_SUN)).isTrue();
        assertThat(LightRule.tooDark(DirectSun.FULL_SUN, DirectSun.FULL_SUN)).isFalse();
        assertThat(LightRule.tooDark(DirectSun.PART_SHADE, DirectSun.SHADE)).isFalse();
        assertThat(LightRule.tooDark(DirectSun.SHADE, DirectSun.SHADE)).isFalse();
    }
}
