package app.rootology.facts;

import app.rootology.TestcontainersConfiguration;
import app.rootology.facts.KnowledgeGraph.Affecting;
import app.rootology.facts.KnowledgeGraph.CropPair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** The knowledge base loaded into Neo4j at startup, next to (and apart from) the suite's dataset. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class KnowledgeGraphTest {

    @Autowired
    KnowledgeGraph knowledge;

    @Autowired
    ClimateWindows climate;

    @Autowired
    ClimateCells cells;

    @Test
    void holdsTheCuratedKnowledge() {
        assertThat(knowledge.count("Crop")).isEqualTo(39);
        assertThat(knowledge.count("Family")).isEqualTo(11);
        assertThat(knowledge.count("Organism")).isEqualTo(24);
        // Only curated windows are written now (#62): CENTRO and the one NORD row; SUD is derived.
        assertThat(knowledge.count("Zone")).isEqualTo(2);
        assertThat(knowledge.count("Source")).isEqualTo(14);
    }

    @Test
    void knowsWhichTemperatureKillsYoungPlants() {
        assertThat(knowledge.killingTemperatures()).containsEntry("garlic", -5.0).containsEntry("lettuce", 0.0)
                .doesNotContainKey("tomato");
    }

    @Test
    void pairsKeepTheirEvidenceAndSourcesAndAreNeverReversed() {
        assertThat(knowledge.pairsOf("tomato")).contains(
                new CropPair("tomato", "basil", "COMPANION",
                        "Less whitefly when combined with insect nets; basil alone not shown", "study",
                        java.util.List.of("agronet-basil-2016", "cornell-1999")))
                .anySatisfy(p -> {
                    assertThat(p.partnerId()).isEqualTo("potato");
                    assertThat(p.relation()).isEqualTo("ANTAGONIST");
                    assertThat(p.evidence()).isEqualTo("extension");
                });
        assertThat(knowledge.pairsOf("basil")).extracting(CropPair::partnerId).doesNotContain("tomato");
    }

    @Test
    void theCalendarsAreDerivedFromTheSiteProfile() {
        // Vasto, the CENTRO reference: its own curated windows, not an inference.
        assertThat(climate.windowFacts("tomato", ReferenceSite.CENTRO.profile(cells), null)).contains(
                new SourcedFact(new Fact("tomato", "sowIn", "FEBRUARY"), java.util.List.of("rootology-editorial")),
                new SourcedFact(new Fact("tomato", "plantIn", "APRIL"), java.util.List.of("rootology-editorial")),
                new SourcedFact(new Fact("tomato", "harvestIn", "AUGUST"), java.util.List.of("rootology-editorial")));
        // Milan derives tomato with the rule and the site's sources.
        assertThat(climate.windowFacts("tomato", ReferenceSite.NORD.profile(cells), null)).contains(
                new SourcedFact(new Fact("tomato", "plantIn", "APRIL"),
                        java.util.List.of("rootology-editorial", "agera5", "copernicus-dem-90m"),
                        FrostShiftRule.ID));
        // Milan's own cell: the tomato transplant does not move (first safe month April, field starts April).
        assertThat(climate.windowFacts("tomato", ReferenceSite.NORD.profile(cells), null))
                .filteredOn(f -> f.fact().predicate().equals("plantIn"))
                .extracting(SourcedFact::fact).contains(new Fact("tomato", "plantIn", "APRIL"));
        // A zone named in the question keeps its curated rows: broad bean's NORD February sowing.
        assertThat(climate.windowFacts("Fava", ReferenceSite.NORD.profile(cells), null))
                .filteredOn(f -> f.fact().predicate().equals("sowIn"))
                .singleElement().satisfies(f -> {
                    // The curated NORD row wins over the derived autumn sowing of the same crop and activity.
                    assertThat(f.fact().object()).isEqualTo("FEBRUARY");
                    assertThat(f.derivedBy()).isNull();
                });
        assertThat(KnowledgeGraph.months(11, 2)).containsExactly(11, 12, 1, 2);
    }

    @Test
    void factsAboutACropUseTheQuestionsNameAndKeepTheirSources() {
        assertThat(knowledge.factsAbout("tomato")).contains(
                new SourcedFact(new Fact("tomato", "companionOf", "Basil"), java.util.List.of("agronet-basil-2016", "cornell-1999")),
                new SourcedFact(new Fact("tomato", "antagonistOf", "Potato"), java.util.List.of("purdue-bp80", "cornell-1999")),
                new SourcedFact(new Fact("Late blight", "affects", "tomato"), java.util.List.of("rootology-editorial", "eppo-codes")))
                .extracting(SourcedFact::fact).contains(new Fact("Late blight", "activeInSeason", "SUMMER"));
        assertThat(knowledge.factsAbout("Pomodoro")).extracting(SourcedFact::fact)
                .contains(new Fact("Pomodoro", "companionOf", "Basilico"));
        assertThat(knowledge.factsAbout("Lavender")).isEmpty();
        assertThat(knowledge.factsAbout("tomato", "it")).extracting(SourcedFact::fact)
                .contains(new Fact("tomato", "companionOf", "Basilico"), new Fact("Peronospora del pomodoro e della patata",
                        "affects", "tomato"));
    }

    @Test
    void factsAboutAPestGiveItsSeasonsAndHosts() {
        assertThat(knowledge.factsAbout("Carrot fly")).extracting(SourcedFact::fact).contains(
                new Fact("Carrot fly", "activeInSeason", "SPRING"),
                new Fact("Carrot fly", "activeInSeason", "SUMMER"),
                new Fact("Carrot fly", "activeInSeason", "AUTUMN"),
                new Fact("Carrot fly", "affects", "Carrot"))
                .doesNotContain(new Fact("Carrot fly", "activeInSeason", "WINTER"));
        assertThat(KnowledgeGraph.seasons(10, 2)).containsExactly("WINTER", "AUTUMN");
    }

    @Test
    void sowingFactsListWhatCanBeSownInAMonth() {
        assertThat(climate.sowingFacts("MARCH", ReferenceSite.CENTRO.profile(cells), "it"))
                .extracting(SourcedFact::fact).contains(
                        new Fact("Pomodoro", "sowIn", "MARCH"), new Fact("Patata", "plantIn", "MARCH"))
                .doesNotContain(new Fact("Zucchina", "sowIn", "MARCH"));
        assertThat(climate.sowingFacts("NOT-A-MONTH", ReferenceSite.CENTRO.profile(cells), "en")).isEmpty();
    }

    @Test
    void pestsAreActiveOnlyInTheirMonths() {
        assertThat(knowledge.affecting("carrot", 5)).extracting(Affecting::organismId).contains("PSILRO");
        assertThat(knowledge.affecting("carrot", 7)).extracting(Affecting::organismId).doesNotContain("PSILRO");
        assertThat(knowledge.affecting("tomato", 8)).extracting(Affecting::organismId)
                .contains("PHYTIN", "GNORAB", "TRIAVA");
    }
}
