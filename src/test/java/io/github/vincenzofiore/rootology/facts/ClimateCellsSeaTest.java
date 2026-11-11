package io.github.vincenzofiore.rootology.facts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The hint that typed coordinates are in open sea (#66): only inside the grid, never on a coast or an island near one. */
class ClimateCellsSeaTest {

    private static final ClimateCells CELLS = new ClimateCells();

    @Test
    void openSeaInsideTheGridLooksLikeSea() {
        assertThat(CELLS.looksLikeSea(40.0, 12.0)).as("the Tyrrhenian Sea").isTrue();
        assertThat(CELLS.looksLikeSea(42.5, 5.5)).as("the Gulf of Lion, off shore").isTrue();
    }

    @Test
    void landCoastsAndIslandsNearACoastDoNot() {
        assertThat(CELLS.looksLikeSea(41.9, 12.5)).as("Rome").isFalse();
        assertThat(CELLS.looksLikeSea(42.12, 14.71)).as("Vasto, on the coast").isFalse();
        assertThat(CELLS.looksLikeSea(40.55, 14.24)).as("Capri, near the coast").isFalse();
        assertThat(CELLS.looksLikeSea(38.47, 14.95)).as("Lipari").isFalse();
    }

    @Test
    void aSmallIslandFarFromACoastStillLooksLikeSeaSoItIsOnlyAHint() {
        // Lampedusa's cell has less than 25 % land: the dialog asks, and a second Save keeps the position.
        assertThat(CELLS.looksLikeSea(35.50, 12.60)).isTrue();
    }

    @Test
    void outsideTheGridNothingIsSaid() {
        assertThat(CELLS.looksLikeSea(40.71, -74.0)).as("New York").isFalse();
        assertThat(CELLS.looksLikeSea(-33.9, 151.2)).as("Sydney").isFalse();
        assertThat(CELLS.looksLikeSea(20.0, -40.0)).as("the open Atlantic, beyond the grid").isFalse();
    }
}
