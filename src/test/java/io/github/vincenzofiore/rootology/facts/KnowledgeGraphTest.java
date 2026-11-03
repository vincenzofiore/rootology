package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph.Affecting;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph.CropPair;
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

    @Test
    void holdsTheCuratedKnowledge() {
        assertThat(knowledge.count("Crop")).isEqualTo(39);
        assertThat(knowledge.count("Family")).isEqualTo(11);
        assertThat(knowledge.count("Organism")).isEqualTo(24);
        assertThat(knowledge.count("Zone")).isEqualTo(3);
        assertThat(knowledge.count("Source")).isEqualTo(9);
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
    void windowsCarryTheirProvenance() {
        assertThat(knowledge.windows("tomato", "NORD")).anySatisfy(w -> {
            assertThat(w.activity()).isEqualTo("TRANSPLANT");
            assertThat(w.fromMonth()).isEqualTo(5);
            assertThat(w.toMonth()).isEqualTo(6);
            assertThat(w.provenance()).isEqualTo("INFERRED");
            assertThat(w.derivedBy()).isEqualTo(ZoneShiftRule.ID);
            assertThat(w.sources()).containsExactly("rootology-editorial");
        });
        assertThat(knowledge.windows("broad-bean", "NORD")).filteredOn(w -> w.activity().equals("SOW"))
                .singleElement().satisfies(w -> {
                    assertThat(w.provenance()).isEqualTo("CURATED");
                    assertThat(w.fromMonth()).isEqualTo(2);
                });
        assertThat(knowledge.windows("tomato", "CENTRO")).allMatch(w -> w.provenance().equals("CURATED"));
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
    void pestsAreActiveOnlyInTheirMonths() {
        assertThat(knowledge.affecting("carrot", 5)).extracting(Affecting::organismId).contains("PSILRO");
        assertThat(knowledge.affecting("carrot", 7)).extracting(Affecting::organismId).doesNotContain("PSILRO");
        assertThat(knowledge.affecting("tomato", 8)).extracting(Affecting::organismId)
                .contains("PHYTIN", "GNORAB", "TRIAVA");
    }
}
