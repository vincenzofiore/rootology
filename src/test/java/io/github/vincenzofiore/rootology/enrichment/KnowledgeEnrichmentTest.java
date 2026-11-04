package io.github.vincenzofiore.rootology.enrichment;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.facts.Contribution.Alias;
import io.github.vincenzofiore.rootology.facts.Contribution.Host;
import io.github.vincenzofiore.rootology.facts.Contribution.Pair;
import io.github.vincenzofiore.rootology.facts.KnowledgeContributions;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph.CropPair;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph.Entity;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraphLoaded;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "rootology.knowledge.curators=Cora")
class KnowledgeEnrichmentTest {

    @Autowired
    KnowledgeEnrichment enrichment;

    @Autowired
    KnowledgeContributions contributions;

    @Autowired
    KnowledgeGraph knowledge;

    @Autowired
    SpaceService spaces;

    @Autowired
    Driver driver;

    @Autowired
    ApplicationEventPublisher events;

    private long spaceOf(String login) {
        spaces.signedIn(login);
        return spaces.spacesOf(login).getFirst().id();
    }

    @Test
    void aSpaceFactAppliesAtOnceButOnlyToItsSpace() {
        long mine = spaceOf("sara");
        long theirs = spaceOf("tom");
        Pair pair = new Pair("chili-pepper", "eggplant", "COMPANION", "Same bed, no trouble so far");

        ProposalSummary added = enrichment.propose(mine, "sara", ProposalScope.SPACE, pair, null);

        assertThat(added.status()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(added.reviewedBy()).isNull();
        assertThat(contributions.problems(pair, mine)).containsExactly("this pair is already declared");
        assertThat(contributions.problems(pair, theirs)).isEmpty();
        assertThat(contributions.problems(pair, null)).isEmpty();
        assertThat(knowledge.pairsOf("chili-pepper")).extracting(CropPair::partnerId).doesNotContain("eggplant");
        assertThat(enrichment.ofSpace(mine, "sara")).extracting(ProposalSummary::id).containsExactly(added.id());
        assertThatThrownBy(() -> enrichment.ofSpace(mine, "tom")).isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> enrichment.propose(mine, "tom", ProposalScope.SPACE, pair, null))
                .isInstanceOf(SpaceAccessException.class);
    }

    @Test
    void aGlobalFactNeedsACitationAndACurator() {
        long space = spaceOf("ugo");
        Alias alias = new Alias("tomato", "Pummarola", "it");

        assertThatThrownBy(() -> enrichment.propose(space, "ugo", ProposalScope.GLOBAL, alias, " "))
                .isInstanceOf(InvalidProposalException.class)
                .hasMessageContaining("needs a cited source");
        ProposalSummary proposed = enrichment.propose(space, "ugo", ProposalScope.GLOBAL, alias,
                "Neapolitan dialect, https://it.wikipedia.org/wiki/Lingua_napoletana");
        assertThat(proposed.status()).isEqualTo(ProposalStatus.PENDING);
        assertThat(knowledge.resolve("pummarola")).isEmpty();

        assertThatThrownBy(() -> enrichment.pending("ugo")).isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> enrichment.approve("ugo", proposed.id(), null)).isInstanceOf(SpaceAccessException.class);
        assertThat(enrichment.pending("cora")).extracting(ProposalSummary::id).contains(proposed.id());

        ProposalSummary approved = enrichment.approve("CORA", proposed.id(), "fine");

        assertThat(approved.status()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(approved.reviewedBy()).isEqualTo("cora");
        assertThat(knowledge.resolve("Pummarola")).map(Entity::id).contains("tomato");
        assertThat(contributions.problems(new Alias("basil", "pummarola", "it"), null))
                .containsExactly("\"pummarola\" already names tomato");
        assertThatThrownBy(() -> enrichment.reject("cora", proposed.id(), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRejectedProposalStaysInTheHistoryAndChangesNothing() {
        long space = spaceOf("vera");
        Pair pair = new Pair("zucchini", "cucumber", "ANTAGONIST", "Compete for space");
        ProposalSummary proposed = enrichment.propose(space, "vera", ProposalScope.GLOBAL, pair, "My own beds, 2025");

        ProposalSummary rejected = enrichment.reject("cora", proposed.id(), "Personal experience: keep it in your space");

        assertThat(rejected.status()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(enrichment.ofSpace(space, "vera")).singleElement()
                .extracting(ProposalSummary::reviewNote).isEqualTo("Personal experience: keep it in your space");
        assertThat(knowledge.pairsOf("zucchini")).extracting(CropPair::partnerId).doesNotContain("cucumber");
    }

    @Test
    void proposalsFollowTheCuratedRules() {
        assertThat(contributions.problems(new Pair("potato", "cucumber", "COMPANION", "x"), null))
                .containsExactly("this pair is already declared as antagonist");
        assertThat(contributions.problems(new Pair("tomato", "tomato", "FRIEND", " "), null))
                .contains("a crop cannot pair with itself", "an effect must have 1 to 300 characters")
                .anyMatch(p -> p.startsWith("relation must be one of"));
        assertThat(contributions.problems(new Pair("mango", "tomato", "COMPANION", "x"), null))
                .containsExactly("unknown crop mango");
        assertThat(contributions.problems(new Host("LPTNDE", "tomato", 5, 8), null))
                .containsExactly("this organism is already declared on this crop");
        assertThat(contributions.problems(new Host("XXXXXX", "tomato", 0, 13), null))
                .containsExactly("unknown organism XXXXXX", "month 0 is not between 1 and 12",
                        "month 13 is not between 1 and 12");
        assertThat(contributions.problems(new Alias("tomato", "Pomodoro", "fr"), null))
                .containsExactly("\"Pomodoro\" is already a name of tomato", "language must be one of [it, en], not fr");
    }

    @Test
    void approvedFactsComeBackWhenTheGraphIsRebuilt() {
        long space = spaceOf("walt");
        Host host = new Host("PIERBR", "kohlrabi", 5, 9);
        assertThat(contributions.problems(host, space)).containsExactly("unknown crop kohlrabi");
        Host onRocket = new Host("PIERBR", "rocket", 5, 9);
        enrichment.propose(space, "walt", ProposalScope.SPACE, onRocket, null);
        assertThat(contributions.problems(onRocket, space)).isNotEmpty();

        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run("MATCH ()-[r]->() WHERE r.proposalId IS NOT NULL DELETE r").consume());
            session.executeWrite(tx -> tx.run("MATCH (a:Alias) DETACH DELETE a").consume());
        }
        assertThat(contributions.problems(onRocket, space)).isEmpty();

        events.publishEvent(new KnowledgeGraphLoaded());

        assertThat(contributions.problems(onRocket, space)).containsExactly("this organism is already declared on this crop");
    }
}
