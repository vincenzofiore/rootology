package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import app.rootology.experiments.ExperimentCase;
import app.rootology.experiments.ExperimentRunner;
import app.rootology.experiments.GoldenQuestions;
import app.rootology.experiments.GoldenRuns;
import app.rootology.experiments.RecordedLlmAnswers;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.NoiseProfile;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.pipeline.Question;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One experiment case with the arms side by side: Jena (the arbiter), Claude with and without
 * the skill (recorded by hand), and the pipeline, run live in HUMAN review mode.
 */
@Route(value = "compare", layout = MainLayout.class)
@RolesAllowed("QA")
public class ComparisonView extends VerticalLayout implements HasDynamicTitle {

    private final ExperimentRunner runner;
    private final GardenPipeline pipeline;
    private final CurrentUser currentUser;
    private final FlexLayout arms = new FlexLayout();

    public ComparisonView(ExperimentRunner runner, GardenPipeline pipeline, GoldenRuns goldenRuns,
                          CurrentUser currentUser) {
        this.runner = runner;
        this.pipeline = pipeline;
        this.currentUser = currentUser;

        ComboBox<ExperimentCase> caseBox = new ComboBox<>(getTranslation("qa.case"), ExperimentCase.values());
        caseBox.setItemLabelGenerator(c -> c.id() + " · " + c.question());
        caseBox.setValue(ExperimentCase.EASY_1);
        caseBox.setWidth("32rem");

        ComboBox<String> noiseBox = new ComboBox<>(getTranslation("qa.input"), List.of("Clean facts", "Noisy facts"));
        noiseBox.setItemLabelGenerator(c -> getTranslation("Clean facts".equals(c) ? "qa.input.clean" : "qa.input.noisy"));
        noiseBox.setValue("Clean facts");
        IntegerField seedField = new IntegerField(getTranslation("qa.seed"));
        seedField.setValue(1);
        seedField.setMin(1);
        seedField.setStepButtonsVisible(true);
        seedField.setEnabled(false);
        noiseBox.addValueChangeListener(e -> seedField.setEnabled("Noisy facts".equals(e.getValue())));

        Button ask = new Button(getTranslation("qa.ask"), e -> show(caseBox.getValue(), noise(noiseBox.getValue(), seedField.getValue())));
        ask.addThemeName("primary");

        HorizontalLayout controls = new HorizontalLayout(caseBox, noiseBox, seedField, ask);
        controls.setDefaultVerticalComponentAlignment(Alignment.END);
        controls.setWrap(true);

        arms.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        arms.getStyle().set("gap", "1rem");
        add(new PageHeader(getTranslation("qa.title")));
        add(new H3(getTranslation("qa.experiment")), controls, new Paragraph(getTranslation("qa.experiment.intro")),
                arms);
        add(goldenSection(goldenRuns));
    }

    /**
     * The golden questions (#20) with the latest recorded live run. Live runs are not started from here: they
     * need fixtures (spaces, gardens, a fixed date, a simulated forecast) that do not belong in this database.
     */
    private VerticalLayout goldenSection(GoldenRuns goldenRuns) {
        Optional<GoldenRuns.LiveRun> latest = goldenRuns.latest();
        Map<String, GoldenRuns.LiveResult> live = latest.map(GoldenRuns.LiveRun::results).orElse(Map.of());
        Grid<GoldenQuestions.Case> grid = new Grid<>();
        grid.addColumn(GoldenQuestions.Case::id).setHeader(getTranslation("qa.case")).setAutoWidth(true);
        grid.addColumn(GoldenQuestions.Case::question).setHeader(getTranslation("history.question")).setFlexGrow(2);
        grid.addColumn(c -> c.answer() != null ? String.join(", ", c.answer()) : getTranslation("qa.toperson", c.escalate()))
                .setHeader(getTranslation("qa.expected")).setFlexGrow(2);
        grid.addColumn(c -> Optional.ofNullable(live.get(c.id())).map(GoldenRuns.LiveResult::verdict).orElse("-"))
                .setHeader(getTranslation("qa.lastlive")).setAutoWidth(true);
        grid.addColumn(c -> Optional.ofNullable(live.get(c.id())).map(r -> "%.2f".formatted(r.confidence())).orElse(""))
                .setHeader(getTranslation("history.confidence")).setAutoWidth(true);
        grid.addColumn(c -> Optional.ofNullable(live.get(c.id())).map(r -> r.escalationReason() != null
                        ? getTranslation("qa.toperson", r.escalationReason()) : String.join(", ", r.answer())).orElse(""))
                .setHeader(getTranslation("qa.outcome")).setFlexGrow(2);
        grid.addColumn(c -> Optional.ofNullable(live.get(c.id())).map(r -> r.intentMatches() ? getTranslation("common.yes")
                : getTranslation("common.no") + ": " + r.understoodIntent()).orElse(""))
                .setHeader(getTranslation("qa.understood")).setAutoWidth(true);
        grid.setItems(goldenRuns.golden().cases());
        grid.setAllRowsVisible(true);
        String summary = latest.map(r -> getTranslation("qa.summary", r.startedAt(), r.llm(), r.decisionLayer(),
                String.valueOf(r.verdicts()), r.intentsUnderstood(), r.results().size()))
                .orElse(getTranslation("qa.norun"));
        return new VerticalLayout(new H3(getTranslation("qa.golden")), new Paragraph(getTranslation("qa.golden.intro")),
                new Paragraph(summary), grid);
    }

    private static NoiseProfile noise(String choice, Integer seed) {
        // Same noise as the HOLDOUT recordings: 15% dropout, 2 false facts near the subject, 20% wrong tags.
        return "Noisy facts".equals(choice)
                ? new NoiseProfile(0.15, 2, 0.2, true, seed == null ? 1 : seed)
                : NoiseProfile.NONE;
    }

    private void show(ExperimentCase experimentCase, NoiseProfile noise) {
        if (experimentCase == null) {
            return;
        }
        arms.removeAll();
        List<String> jena = runner.jenaAnswer(experimentCase);
        arms.add(card(getTranslation("qa.arm.jena"), values(jena), getTranslation("qa.arm.jena.how")));
        arms.add(recorded(getTranslation("qa.arm.skill"), RecordedLlmAnswers.withSkill(experimentCase), jena));
        arms.add(recorded(getTranslation("qa.arm.noskill"), RecordedLlmAnswers.withoutSkill(experimentCase), jena));
        try {
            GardenPipeline.Run run = pipeline.start(new Question(experimentCase.id(), experimentCase.question()), noise,
                    currentUser.runContext(currentUser.space().id(), 0));
            arms.add(run.answer() != null ? pipelineCard(run.answer(), jena)
                    : card(getTranslation("qa.arm.pipeline"), getTranslation("qa.waiting"),
                    getTranslation("qa.waiting.why", run.pending().decision().reason())));
        } catch (RuntimeException ex) {
            String failure = UiErrors.text(this, ex);
            Toasts.problem(failure);
            arms.add(card(getTranslation("qa.arm.pipeline"), getTranslation("qa.failed.short"), failure));
        }
    }

    private Div recorded(String title, Optional<List<String>> answer, List<String> jena) {
        return answer.map(a -> card(title, values(a), matchLabel(a, jena) + " · " + getTranslation("qa.byhand")))
                .orElseGet(() -> card(title, getTranslation("qa.notrecorded"), getTranslation("qa.notrecorded.why")));
    }

    private Div pipelineCard(PipelineAnswer answer, List<String> jena) {
        String status = answer.answeredByHuman() ? getTranslation("history.byhuman", answer.reviewer())
                : getTranslation(answer.escalated() ? "qa.escalated" : "qa.notescalated");
        return card(getTranslation("qa.arm.pipeline"), values(answer.answer()),
                matchLabel(answer.answer(), jena) + " · " + getTranslation("qa.confidence", answer.confidence()) + " · " + status,
                answer.text());
    }

    private String matchLabel(List<String> answer, List<String> jena) {
        return getTranslation(new HashSet<>(answer).equals(new HashSet<>(jena)) ? "qa.matches" : "qa.differs");
    }

    private String values(List<String> values) {
        return values.isEmpty() ? getTranslation("qa.nothing") : String.join(", ", values);
    }

    private Div card(String title, String value, String... details) {
        Div card = new Div(new H3(title), new Span(value));
        for (String detail : details) {
            Paragraph p = new Paragraph(detail);
            p.getStyle().set("font-size", "0.85rem").set("margin", "0.4rem 0 0");
            card.add(p);
        }
        card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "8px").set("padding", "0.8rem 1rem").set("width", "15rem");
        return card;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("qa.title") + " · Rootology";
    }
}
