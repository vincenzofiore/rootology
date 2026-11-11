package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.facts.Fact;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Checks the request sent to Jev and how its answers become a Decision, against a canned response. */
class JevDecisionLayerTest {

    private static final GraphFacts FACTS = GraphFacts.curated(List.of(
            new Fact("Tomato", "companionOf", "Basil"),
            new Fact("Basil", "companionOf", "Tomato"),
            new Fact("Basil", "repels", "Mosquito")));

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://jev.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final JevDecisionLayer layer = new JevDecisionLayer(builder.build(), "jev-latest");

    private void respond(String answersJson) {
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.model").value("jev-latest"))
                .andExpect(jsonPath("$.state.facts[0].predicate").value("companionOf"))
                .andExpect(jsonPath("$.state.facts[0].source").value("CURATED"))
                .andExpect(jsonPath("$.questions.answerable.type").value("noul"))
                .andRespond(withSuccess("{\"model\":\"jev-1\",\"answers\":" + answersJson + "}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void sendsStateFieldsInAFixedOrder() {
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).containsSubsequence(
                            "\"model\"", "\"state\"", "\"intent\"", "\"rule\"", "\"sources\"",
                            "\"CURATED\"", "\"UNCONFIRMED\"", "\"facts\"",
                            "\"subject\"", "\"predicate\"", "\"object\"", "\"source\"", "\"questions\"");
                })
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.04}}}""", MediaType.APPLICATION_JSON));

        layer.decide(Intent.of(QueryKind.ACTIVE_SEASONS, "Mosquito"), FACTS);

        server.verify();
    }

    @Test
    void anInferredReadingNamesItsRuleAndAddsTheInferredSourceDescription() {
        var facts = new GraphFacts(List.of(
                Reading.curated(new Fact("Tomato", "companionOf", "Basil")),
                Reading.inferred(new Fact("Basil", "companionOf", "Tomato"), "some-rule-v1", List.of("cornell-1999"))));
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.state.sources.INFERRED").exists())
                .andExpect(jsonPath("$.state.facts[1].source").value("INFERRED"))
                .andExpect(jsonPath("$.state.facts[1].derivedBy").value("some-rule-v1"))
                .andExpect(jsonPath("$.state.facts[0].derivedBy").doesNotExist())
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.9},
                         "candidate_0":{"type":"noul","noul":0.1},
                         "candidate_1":{"type":"noul","noul":0.9}}}""", MediaType.APPLICATION_JSON));

        layer.decide(Intent.of(QueryKind.COMPANIONS, "Tomato"), facts);

        server.verify();
    }

    @Test
    void aDeclaredReadingAddsTheDeclaredSourceDescription() {
        var facts = new GraphFacts(List.of(new Reading(new Fact("Davanzale", "hasDirectSun", "SHADE"),
                Reading.Source.DECLARED, List.of("garden"), null)));
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.state.sources.DECLARED").exists())
                .andExpect(jsonPath("$.state.sources.PROPOSED").doesNotExist())
                .andExpect(jsonPath("$.state.facts[0].source").value("DECLARED"))
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.9}}}""", MediaType.APPLICATION_JSON));

        layer.decide(Intent.of(QueryKind.SOW_IN_PLOT, "Davanzale"), facts);

        server.verify();
    }

    @Test
    void aProposedReadingAddsTheProposedSourceDescription() {
        var facts = new GraphFacts(List.of(
                Reading.curated(new Fact("Tomato", "companionOf", "Basil")),
                new Reading(new Fact("Tomato", "companionOf", "Lettuce"), Reading.Source.PROPOSED,
                        List.of("proposal-7"), null)));
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.state.sources.PROPOSED").exists())
                .andExpect(jsonPath("$.state.sources.INFERRED").doesNotExist())
                .andExpect(jsonPath("$.state.sources.DECLARED").doesNotExist())
                .andExpect(jsonPath("$.state.facts[1].source").value("PROPOSED"))
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.9},
                         "candidate_0":{"type":"noul","noul":0.9},
                         "candidate_1":{"type":"noul","noul":0.7},
                         "candidate_2":{"type":"noul","noul":0.1}}}""", MediaType.APPLICATION_JSON));

        layer.decide(Intent.of(QueryKind.COMPANIONS, "Tomato"), facts);

        server.verify();
    }

    @Test
    void whenJevFailsAPersonDecidesAndRepeatedFailuresStopTheCalls() {
        for (int i = 0; i < io.github.vincenzofiore.rootology.resilience.CircuitBreaker.FAILURE_THRESHOLD; i++) {
            server.expect(requestTo("https://jev.test/v1/systemone"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
        }

        Decision first = layer.decide(Intent.of(QueryKind.COMPANIONS, "Tomato"), FACTS);
        layer.decide(Intent.of(QueryKind.COMPANIONS, "Tomato"), FACTS);
        layer.decide(Intent.of(QueryKind.COMPANIONS, "Tomato"), FACTS);
        Decision open = layer.decide(Intent.of(QueryKind.COMPANIONS, "Tomato"), FACTS);

        assertThat(first.escalate()).isTrue();
        assertThat(first.reason()).isEqualTo("the decision layer did not answer");
        assertThat(open.reason()).isEqualTo("the decision layer is not available right now");
        server.verify(); // exactly three calls: the fourth was not made
    }

    @Test
    void factsOfTheKnowledgeBaseGetTheirOwnRuleAndNoneCountsAsAnAnswer() {
        var garlic = new GraphFacts(List.of(new Reading(new Fact("Ruggine", "affects", "aglio"),
                Reading.Source.CURATED, List.of("eppo-codes"), null)));
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.questions.answerable.instructions")
                        .value(org.hamcrest.Matchers.containsString("the answer is that there is none")))
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.9}}}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.state.rule").value(org.hamcrest.Matchers.containsString("no pest is excluded")))
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.9}}}""", MediaType.APPLICATION_JSON));

        Decision none = layer.decide(new Intent(QueryKind.COMPANIONS, "aglio", null, "it"), garlic);
        layer.decide(new Intent(QueryKind.RELEVANT_PESTS, "aglio", "SUMMER", "it"), garlic);

        assertThat(none.escalate()).isFalse();
        assertThat(none.answer()).isEmpty();
        server.verify();
    }

    @Test
    void onTheKnowledgeBaseTheSubjectIsNeverItsOwnAntagonist() {
        var fennel = new GraphFacts(List.of(
                new Reading(new Fact("finocchio", "antagonistOf", "Fagiolo"), Reading.Source.CURATED,
                        List.of("cornell-1999"), null),
                new Reading(new Fact("finocchio", "antagonistOf", "Peperone"), Reading.Source.CURATED,
                        List.of("cornell-1999"), null)));
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.questions.candidate_0.instructions")
                        .value(org.hamcrest.Matchers.containsString("\"Fagiolo\"")))
                .andExpect(jsonPath("$.questions.candidate_2").doesNotExist())
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.95},
                         "candidate_0":{"type":"noul","noul":0.95},
                         "candidate_1":{"type":"noul","noul":0.95}}}""", MediaType.APPLICATION_JSON));

        Decision decision = layer.decide(new Intent(QueryKind.ANTAGONISTS, "finocchio", null, "it"), fennel);

        assertThat(decision.answer()).containsExactly("Fagiolo", "Peperone");
        server.verify();
    }

    @Test
    void theSuitesFactsKeepTheRecordedWording() {
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.state.rule").value(JevDecisionLayer.rule(QueryKind.COMPANIONS)))
                .andExpect(jsonPath("$.questions.answerable.instructions")
                        .value(org.hamcrest.Matchers.containsString("or if no fact declares the relation")))
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.04}}}""", MediaType.APPLICATION_JSON));

        layer.decide(Intent.of(QueryKind.COMPANIONS, "Tomato"), FACTS);

        server.verify();
    }

    @Test
    void theQuestionsLanguageNeverReachesJev() {
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.state.intent.subject").value("Mosquito"))
                .andExpect(jsonPath("$.state.intent.language").doesNotExist())
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.04}}}""", MediaType.APPLICATION_JSON));

        layer.decide(new Intent(QueryKind.ACTIVE_SEASONS, "Mosquito", null, "it"), FACTS);

        server.verify();
    }

    @Test
    void withoutInferredReadingsTheRequestIsUnchanged() {
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.state.sources.INFERRED").doesNotExist())
                .andExpect(jsonPath("$.state.sources.PROPOSED").doesNotExist())
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.04}}}""", MediaType.APPLICATION_JSON));

        layer.decide(Intent.of(QueryKind.ACTIVE_SEASONS, "Mosquito"), FACTS);

        server.verify();
    }

    @Test
    void candidatesArePlantsForCompanionQuestions() {
        assertThat(JevDecisionLayer.candidates(QueryKind.COMPANIONS, FACTS)).containsExactly("Tomato", "Basil");
    }

    @Test
    void keepsCandidatesJevJudgesAsPartOfTheAnswer() {
        respond("""
                {"answerable":{"type":"noul","noul":0.98},
                 "candidate_0":{"type":"noul","noul":0.03},
                 "candidate_1":{"type":"noul","noul":0.96}}""");

        Decision decision = layer.decide(Intent.of(QueryKind.COMPANIONS, "Tomato"), FACTS);

        assertThat(decision.answer()).containsExactly("Basil");
        assertThat(decision.confidence()).isCloseTo(0.92, offset(1e-9));
        assertThat(decision.escalate()).isFalse();
        server.verify();
    }

    @Test
    void escalatesWhenJevIsUnsure() {
        respond("""
                {"answerable":{"type":"noul","noul":0.9},
                 "candidate_0":{"type":"noul","noul":0.6},
                 "candidate_1":{"type":"noul","noul":0.95}}""");

        Decision decision = layer.decide(Intent.of(QueryKind.COMPANIONS, "Tomato"), FACTS);

        assertThat(decision.escalate()).isTrue();
        assertThat(decision.reason()).startsWith("Jev: low confidence");
    }

    @Test
    void escalatesWhenFactsDoNotAnswer() {
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andRespond(withSuccess("""
                        {"answers":{"answerable":{"type":"noul","noul":0.04}}}""", MediaType.APPLICATION_JSON));

        Decision decision = layer.decide(Intent.of(QueryKind.ACTIVE_SEASONS, "Mosquito"), FACTS);

        assertThat(decision.answer()).isEmpty();
        assertThat(decision.escalate()).isTrue();
        assertThat(decision.reason()).contains("do not answer");
    }
}
