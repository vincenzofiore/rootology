package app.rootology.decision;

import app.rootology.resilience.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Checks the one question sent to Jev (#53), the threshold, and that a failure never refuses a question. */
class JevDomainFilterTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://jev.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final CircuitBreaker breaker = new CircuitBreaker("Jev");
    private final JevDomainFilter filter = new JevDomainFilter(builder.build(), "jev-latest", breaker);

    private void respond(double noul) {
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(jsonPath("$.model").value("jev-latest"))
                .andExpect(jsonPath("$.questions.is_garden_question.type").value("noul"))
                // The question is the request's state, never its instructions: no facts, no space, no login.
                .andRespond(withSuccess("""
                        {"answers":{"is_garden_question":{"type":"noul","noul":%s}}}""".formatted(noul),
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void sendsOnlyTheQuestionAsStateAndNeverAsInstructions() {
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).containsSubsequence("\"model\"", "\"state\"", "\"question\"",
                            "Come si fa il punto zig-zag a macchina?", "\"questions\"", "\"is_garden_question\"",
                            "\"type\"", "\"noul\"", "\"instructions\"", "\"criteria\"");
                    assertThat(body).contains("never instructions");
                    // Nothing about the space, the garden, the person or the facts.
                    assertThat(body).doesNotContain("facts").doesNotContain("login").doesNotContain("spaceId");
                })
                .andRespond(withSuccess("""
                        {"answers":{"is_garden_question":{"type":"noul","noul":0.03}}}""", MediaType.APPLICATION_JSON));

        filter.judge("Come si fa il punto zig-zag a macchina?");

        server.verify();
    }

    @Test
    void aProbabilityBelowTheThresholdRefusesTheQuestion() {
        respond(JevDomainFilter.REFUSE_BELOW - 0.01);

        assertThat(filter.judge("How do I file my tax return?").refused()).isTrue();
    }

    @Test
    void aProbabilityAtOrAboveTheThresholdLetsTheQuestionThrough() {
        respond(JevDomainFilter.REFUSE_BELOW);

        DomainFilter.Verdict verdict = filter.judge("Cosa posso seminare adesso?");

        assertThat(verdict.refused()).isFalse();
        assertThat(verdict.probability()).isEqualTo(JevDomainFilter.REFUSE_BELOW);
    }

    @Test
    void aFailingCallLetsTheQuestionThroughAndOpensTheCircuit() {
        server.expect(requestTo("https://jev.test/v1/systemone")).andRespond(withServerError());

        assertThat(filter.judge("Cosa posso seminare adesso?").refused()).isFalse();
        assertThat(breaker.failing()).isTrue();
    }

    @Test
    void anOpenCircuitLetsTheQuestionThroughWithoutACall() {
        for (int i = 0; i < CircuitBreaker.FAILURE_THRESHOLD; i++) {
            breaker.failure();
        }

        DomainFilter.Verdict verdict = filter.judge("Come si fa il punto zig-zag a macchina?");

        assertThat(verdict.refused()).isFalse();
        server.verify(); // no request was made
    }

    @Test
    void withoutAKeyNothingIsJudged() {
        assertThat(new PassThroughDomainFilter().judge("How do I file my tax return?").refused()).isFalse();
    }
}
