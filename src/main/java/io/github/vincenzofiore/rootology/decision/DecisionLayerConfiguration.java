package io.github.vincenzofiore.rootology.decision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Uses Jev when a TypeSafe API key is configured, otherwise the deterministic stub. */
@Configuration
class DecisionLayerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DecisionLayerConfiguration.class);

    @Bean
    DecisionLayer decisionLayer(@Value("${rootology.jev.api-key:}") String apiKey,
                                @Value("${rootology.jev.base-url}") String baseUrl,
                                @Value("${rootology.jev.model}") String model) {
        if (apiKey.isBlank()) {
            log.info("No TypeSafe API key: using StubDecisionLayer");
            return new StubDecisionLayer();
        }
        log.info("Using JevDecisionLayer with model {}", model);
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        return new JevDecisionLayer(restClient, model);
    }
}
