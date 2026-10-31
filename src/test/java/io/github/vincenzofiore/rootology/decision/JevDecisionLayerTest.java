package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.inference.facts.Fact;
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
