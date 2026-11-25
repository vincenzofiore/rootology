package app.rootology.pipeline;

import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.DecisionLayer;
import app.rootology.decision.DomainFilter;
import app.rootology.weather.WeatherService;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * One Spring context for the pipeline tests (#174): the same properties and doubles in every subclass, so
 * the context cache starts it once. Spies answer as the real beans until a test stubs them with
 * {@code doReturn}.
 */
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"rootology.jev.api-key=", "rootology.notifications.group-window=PT0S"})
abstract class PipelineContext extends EmbabelMockitoIntegrationTest {

    /** Never the real service in tests: by default it has no forecast, as when MET Norway is down. */
    @MockitoBean
    WeatherService weatherService;

    /** Without a key the real filter passes every question; a test stubs what Jev would answer. */
    @MockitoSpyBean
    DomainFilter domainFilter;

    @MockitoSpyBean
    DecisionLayer decisionLayer;
}
