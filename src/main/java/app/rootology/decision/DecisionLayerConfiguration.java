package app.rootology.decision;

import app.rootology.resilience.CircuitBreakers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Uses Jev when a TypeSafe API key is configured, otherwise the deterministic stub. */
@Configuration
class DecisionLayerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DecisionLayerConfiguration.class);

    private static final String JEV = "Jev";

    @Bean
    DecisionLayer decisionLayer(@Value("${rootology.jev.api-key:}") String apiKey,
                                @Value("${rootology.jev.base-url}") String baseUrl,
                                @Value("${rootology.jev.model}") String model, CircuitBreakers circuits) {
        if (apiKey.isBlank()) {
            log.info("No TypeSafe API key: using StubDecisionLayer");
            return new StubDecisionLayer();
        }
        log.info("Using JevDecisionLayer with model {}", model);
        // Explicit timeouts: the JDK default would wait forever on a silent server (#29).
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(20));
        return new JevDecisionLayer(client(apiKey, baseUrl, timeouts), model, circuits.forService(JEV));
    }

    /**
     * The domain filter (#53): the same Jev and the same circuit, with short timeouts because its call comes
     * before everything else in a question. Without a key nothing is judged: a question is never refused.
     */
    @Bean
    DomainFilter domainFilter(@Value("${rootology.jev.api-key:}") String apiKey,
                              @Value("${rootology.jev.base-url}") String baseUrl,
                              @Value("${rootology.jev.model}") String model, CircuitBreakers circuits) {
        if (apiKey.isBlank()) {
            return new PassThroughDomainFilter();
        }
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        return new JevDomainFilter(client(apiKey, baseUrl, timeouts), model, circuits.forService(JEV));
    }

    private static RestClient client(String apiKey, String baseUrl, SimpleClientHttpRequestFactory timeouts) {
        return RestClient.builder()
                .requestFactory(timeouts)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
}
