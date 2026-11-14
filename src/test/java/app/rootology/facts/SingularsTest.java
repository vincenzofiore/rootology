package app.rootology.facts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The singular forms a plural name is tried in (#108). */
class SingularsTest {

    @Test
    void italianPluralsGiveTheirSingulars() {
        assertThat(Singulars.of("pomodori")).contains("pomodoro");
        assertThat(Singulars.of("peperoni")).contains("peperone");
        assertThat(Singulars.of("finocchi")).contains("finocchio");
        assertThat(Singulars.of("fave")).contains("fava");
        assertThat(Singulars.of("zucche")).contains("zucca");
        assertThat(Singulars.of("lattughe")).contains("lattuga");
        assertThat(Singulars.of("Cavoli neri")).contains("cavolo nero");
    }

    @Test
    void englishPluralsGiveTheirSingulars() {
        assertThat(Singulars.of("tomatoes")).contains("tomato");
        assertThat(Singulars.of("strawberries")).contains("strawberry");
        assertThat(Singulars.of("radishes")).contains("radish");
        assertThat(Singulars.of("broad beans")).contains("broad bean");
    }

    @Test
    void shortWordsAndTheNameItselfAreNotGuessed() {
        assertThat(Singulars.of("pomodoro")).doesNotContain("pomodoro");
        assertThat(Singulars.of("ai")).isEmpty();
    }
}
