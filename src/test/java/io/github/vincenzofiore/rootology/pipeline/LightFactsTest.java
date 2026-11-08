package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.decision.Note;
import io.github.vincenzofiore.rootology.decision.Reading;
import io.github.vincenzofiore.rootology.facts.Fact;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph;
import io.github.vincenzofiore.rootology.facts.SourcedFact;
import io.github.vincenzofiore.rootology.garden.DirectSun;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.LightRule;
import io.github.vincenzofiore.rootology.garden.Orientation;
import io.github.vincenzofiore.rootology.garden.PlotKind;
import io.github.vincenzofiore.rootology.garden.Soil;
import io.github.vincenzofiore.rootology.garden.Wind;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The declared exposure of a plot and light-need-v1 on what could be sown (#54). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LightFactsTest {

    @Autowired
    GardenService gardens;

    @Autowired
    SpaceService spaces;

    @Autowired
    KnowledgeGraph knowledge;

    private LightFacts facts() {
        return new LightFacts(gardens, knowledge);
    }

    private RunContext garden(String login, String plotName, DirectSun sun) {
        spaces.signedIn(login);
        long id = spaces.spacesOf(login).getFirst().id();
        var garden = gardens.saveGarden(id, login, null, "Balcony", "Torino", null, null);
        gardens.savePlot(id, login, garden.id(), null, plotName, PlotKind.CONTAINER, Soil.POTTING_MIX, null,
                Orientation.N, sun, Wind.EXPOSED, 3);
        return new RunContext(id, garden.id(), login);
    }

    /** The context the pipeline resolves for a plot question, in the run's garden (#55). */
    private QuestionContext context(RunContext run, QueryKind kind, String plotName) {
        return new ContextResolver(spaces, gardens, java.time.Clock.systemDefaultZone())
                .resolve(new Intent(kind, plotName, null, "it", null, null), run);
    }

    /** What the knowledge base's sowing facts look like: the crop's name as a subject, sowIn with the month. */
    private static List<SourcedFact> sowable(String... crops) {
        return java.util.Arrays.stream(crops)
                .map(crop -> new SourcedFact(new Fact(crop, "sowIn", "MAGGIO"), List.of("fao-ecocrop"))).toList();
    }

    private static List<String> avoided(List<Reading> readings, String predicate) {
        return readings.stream().filter(r -> r.fact().predicate().equals(predicate))
                .map(r -> r.fact().subject()).toList();
    }

    @Test
    void cropsAskingForMoreLightAreLeftOutWithTheDeclaredSunBehindThem() {
        RunContext run = garden("light-anna", "Davanzale", DirectSun.SHADE);

        LightFacts.Result result = facts().inPlot(context(run, QueryKind.SOW_IN_PLOT, "Davanzale"),
                sowable("Basilico", "Pomodoro", "Broccolo"));

        assertThat(result.readings()).filteredOn(r -> r.source() == Reading.Source.DECLARED)
                .extracting(r -> r.fact().predicate() + "=" + r.fact().object())
                .as("orientation, wind and floor are never sent").containsExactly("hasDirectSun=SHADE");
        assertThat(avoided(result.readings(), "avoidIn")).containsExactly("Basilico", "Pomodoro", "Broccolo");
        assertThat(result.readings()).filteredOn(r -> r.fact().predicate().equals("avoidIn")).allSatisfy(r -> {
            assertThat(r.source()).isEqualTo(Reading.Source.INFERRED);
            assertThat(r.derivedBy()).isEqualTo(LightRule.ID);
            assertThat(r.sourceIds()).containsExactly("garden", "fao-ecocrop");
        });
        assertThat(result.notes()).singleElement().satisfies(n -> {
            assertThat(n.kind()).isEqualTo(Note.Kind.LIGHT_IN_PLOT);
            assertThat(n.arg(0)).isEqualTo("Davanzale");
            assertThat(n.arg(1)).contains("Basilico", "Pomodoro", "Broccolo");
        });
    }

    @Test
    void cropsWithUnknownLightNeedsAreNamedAndNeverGuessed() {
        RunContext run = garden("light-bea", "Davanzale", DirectSun.PART_SHADE);

        LightFacts.Result result = facts().inPlot(context(run, QueryKind.SOW_IN_PLOT, "Davanzale"),
                sowable("Broccolo", "Valerianella (songino)"));

        assertThat(avoided(result.readings(), "avoidIn")).as("cloudy skies: part shade is enough").isEmpty();
        assertThat(result.notes()).singleElement().satisfies(n -> {
            assertThat(n.kind()).isEqualTo(Note.Kind.LIGHT_UNKNOWN);
            assertThat(n.arg(0)).isEqualTo("Valerianella (songino)");
        });
    }

    @Test
    void withoutTheDeclaredSunThereIsNoLightJudgment() {
        RunContext run = garden("light-carla", "Davanzale", null);

        LightFacts.Result result = facts().inPlot(context(run, QueryKind.SOW_IN_PLOT, "Davanzale"),
                sowable("Basilico"));

        assertThat(result.readings()).isEmpty();
        assertThat(result.notes()).isEmpty();
    }

    @Test
    void sowingNowLeavesACropOutOnlyWhenNoPlotGetsEnoughLight() {
        RunContext dark = garden("light-dino", "Davanzale", DirectSun.PART_SHADE);

        LightFacts.Result onlyShade = facts().now(context(dark, QueryKind.SOW_NOW, null), dark,
                sowable("Basilico", "Broccolo"));

        assertThat(avoided(onlyShade.readings(), "avoidNow")).containsExactly("Basilico");
        assertThat(onlyShade.readings()).filteredOn(r -> r.source() == Reading.Source.DECLARED)
                .extracting(r -> r.fact().subject() + "=" + r.fact().object()).containsExactly("Davanzale=PART_SHADE");
        assertThat(onlyShade.notes()).singleElement().extracting(Note::kind).isEqualTo(Note.Kind.LIGHT_ANYWHERE);

        spaces.signedIn("light-elio");
        long id = spaces.spacesOf("light-elio").getFirst().id();
        var terrace = gardens.saveGarden(id, "light-elio", null, "Terrace", "Roma", null, null);
        gardens.savePlot(id, "light-elio", terrace.id(), null, "Shady", PlotKind.CONTAINER, Soil.POTTING_MIX, null,
                null, DirectSun.SHADE, null, null);
        gardens.savePlot(id, "light-elio", terrace.id(), null, "Sunny", PlotKind.CONTAINER, Soil.POTTING_MIX, null,
                null, DirectSun.FULL_SUN, null, null);
        RunContext sunnier = new RunContext(id, terrace.id(), "light-elio");

        LightFacts.Result both = facts().now(context(sunnier, QueryKind.SOW_NOW, null), sunnier, sowable("Basilico"));

        assertThat(both.readings()).as("the sunny plot is enough, so nothing is sent").isEmpty();
        assertThat(both.notes()).isEmpty();
    }

    @Test
    void everyLightOptimumOfTheKnowledgeBaseIsMapped() {
        assertThat(knowledge.lightNeeds().values()).isNotEmpty()
                .allSatisfy(light -> assertThat(LightRule.needs(light)).isPresent());
    }
}
