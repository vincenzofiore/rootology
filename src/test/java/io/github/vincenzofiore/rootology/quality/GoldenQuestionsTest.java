package io.github.vincenzofiore.rootology.quality;

import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.facts.Contribution;
import io.github.vincenzofiore.rootology.facts.KnowledgeContributions;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.PlantingMethod;
import io.github.vincenzofiore.rootology.garden.PlotKind;
import io.github.vincenzofiore.rootology.garden.Soil;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;
import io.github.vincenzofiore.rootology.pipeline.Question;
import io.github.vincenzofiore.rootology.pipeline.RunContext;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.weather.Forecast;
import io.github.vincenzofiore.rootology.weather.ForecastHour;
import io.github.vincenzofiore.rootology.weather.WeatherService;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The offline quality suite (#20): every golden question through the real pipeline, with Claude mocked
 * (it returns the expected intent), the stub decision layer and a fixed "today". Each mismatch fails:
 * a silent wrong answer, a missed escalation, an unexpected escalation or a missing note.
 */
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "rootology.jev.api-key=")
class GoldenQuestionsTest extends EmbabelMockitoIntegrationTest {

    static final ZoneId ITALY = ZoneId.of("Europe/Rome");
    static final GoldenQuestions GOLDEN = GoldenQuestions.load();

    @TestBean
    Clock clock;

    static Clock clock() {
        return Clock.fixed(GOLDEN.today().atTime(10, 0).atZone(ITALY).toInstant(), ITALY);
    }

    @MockitoBean
    WeatherService weather;

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    KnowledgeContributions contributions;

    private final AtomicLong proposalIds = new AtomicLong(900_000);

    @TestFactory
    Stream<DynamicTest> goldenQuestions() {
        whenGenerateText(prompt -> true).thenReturn("phrased answer");
        return GOLDEN.cases().stream().map(c -> DynamicTest.dynamicTest(c.id(), () -> check(c)));
    }

    private void check(GoldenQuestions.Case c) {
        RunContext run = space(c);
        whenCreateObject(prompt -> prompt.contains("Question: " + c.question() + "\n"), Intent.class)
                .thenReturn(c.intent());
        when(weather.around(any(), any())).thenReturn(forecast(c.weather()));

        GardenPipeline.Run result = pipeline.askKnowledgeBase(new Question(c.id(), c.question()), run);

        PipelineAnswer answer = result.answer();
        if (c.answer() == null) {
            if (answer != null) {
                fail("SILENT WRONG ANSWER (missed escalation) for " + c.id() + ": answered " + answer.answer()
                        + ", expected an escalation about \"" + c.escalate() + "\"");
            }
            assertThat(result.pending().decision().reason()).as(c.id() + ": escalation reason").contains(c.escalate());
            return;
        }
        if (answer == null) {
            fail("UNEXPECTED ESCALATION for " + c.id() + ": " + result.pending().decision().reason()
                    + ", expected " + c.answer());
        }
        if (!new HashSet<>(answer.answer()).equals(new HashSet<>(c.answer())) || answer.answer().size() != c.answer().size()) {
            fail("SILENT WRONG ANSWER for " + c.id() + ": answered " + answer.answer() + ", expected " + c.answer());
        }
        for (String note : c.notes()) {
            assertThat(answer.notes()).as(c.id() + ": notes").anyMatch(n -> n.contains(note));
        }
    }

    /** A new space per case, so cases never see each other's garden or facts. */
    private RunContext space(GoldenQuestions.Case c) {
        String login = "golden-" + c.id();
        spaces.signedIn(login);
        long space = spaces.spacesOf(login).getFirst().id();
        switch (c.garden()) {
            case "rome" -> {
                var garden = gardens.saveGarden(space, login, null, "Orto", "Roma", new BigDecimal("41.90"),
                        new BigDecimal("12.50"), "IT");
                var a = gardens.savePlot(space, login, garden.id(), null, "Aiuola A", PlotKind.GROUND, Soil.LOAMY, null);
                var b = gardens.savePlot(space, login, garden.id(), null, "Aiuola B", PlotKind.RAISED_BED, Soil.LOAMY, null);
                gardens.plant(space, login, a.id(), "tomato", PlantingMethod.TRANSPLANTED, LocalDate.of(2026, 9, 1), null);
                gardens.plant(space, login, a.id(), "lettuce", PlantingMethod.SOWN, LocalDate.of(2026, 10, 1), null);
                var pea = gardens.plant(space, login, b.id(), "pea", PlantingMethod.SOWN, LocalDate.of(2026, 3, 1), null);
                gardens.endPlanting(space, login, pea.id(), LocalDate.of(2026, 6, 30));
            }
            case "lyon" -> gardens.saveGarden(space, login, null, "Jardin", "Lyon", new BigDecimal("45.76"),
                    new BigDecimal("4.84"), "FR");
            case "none" -> { }
            default -> throw new IllegalArgumentException("Unknown garden fixture " + c.garden());
        }
        for (GoldenQuestions.SpaceAlias alias : c.spaceFacts()) {
            contributions.apply(proposalIds.incrementAndGet(), space,
                    new Contribution.Alias(alias.targetId(), alias.name(), alias.language()));
        }
        return new RunContext(space, login);
    }

    private Optional<Forecast> forecast(String fixture) {
        return switch (fixture) {
            case "missing" -> Optional.empty();
            case "frost" -> {
                Instant start = clock().instant().truncatedTo(ChronoUnit.HOURS);
                List<ForecastHour> hours = new ArrayList<>();
                for (int i = 0; i < 24 * 7; i++) {
                    hours.add(new ForecastHour(start.plus(i, ChronoUnit.HOURS), -2.0, 80.0, 0.0, 1));
                }
                yield Optional.of(new Forecast(hours, start, false));
            }
            default -> throw new IllegalArgumentException("Unknown weather fixture " + fixture);
        };
    }
}
