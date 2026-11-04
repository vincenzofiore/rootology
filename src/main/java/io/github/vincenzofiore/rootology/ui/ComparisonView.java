package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import io.github.vincenzofiore.rootology.experiments.ExperimentCase;
import io.github.vincenzofiore.rootology.experiments.ExperimentRunner;
import io.github.vincenzofiore.rootology.experiments.RecordedLlmAnswers;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.NoiseProfile;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;
import io.github.vincenzofiore.rootology.pipeline.Question;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * One experiment case with the arms side by side: Jena (the arbiter), Claude with and without
 * the skill (recorded by hand), and the pipeline, run live in HUMAN review mode.
 */
@Route(value = "compare", layout = MainLayout.class)
@PageTitle("Compare · Rootology")
@RolesAllowed("QA")
public class ComparisonView extends VerticalLayout {

    private final ExperimentRunner runner;
    private final GardenPipeline pipeline;
    private final CurrentUser currentUser;
    private final FlexLayout arms = new FlexLayout();

    public ComparisonView(ExperimentRunner runner, GardenPipeline pipeline, CurrentUser currentUser) {
        this.runner = runner;
        this.pipeline = pipeline;
        this.currentUser = currentUser;

        ComboBox<ExperimentCase> caseBox = new ComboBox<>("Case", ExperimentCase.values());
        caseBox.setItemLabelGenerator(c -> c.id() + " · " + c.question());
        caseBox.setValue(ExperimentCase.EASY_1);
        caseBox.setWidth("32rem");

        ComboBox<String> noiseBox = new ComboBox<>("Pipeline input", List.of("Clean facts", "Noisy facts"));
        noiseBox.setValue("Clean facts");
        IntegerField seedField = new IntegerField("Noise seed");
        seedField.setValue(1);
        seedField.setMin(1);
        seedField.setStepButtonsVisible(true);
        seedField.setEnabled(false);
        noiseBox.addValueChangeListener(e -> seedField.setEnabled("Noisy facts".equals(e.getValue())));

        Button ask = new Button("Ask all arms", e -> show(caseBox.getValue(), noise(noiseBox.getValue(), seedField.getValue())));
        ask.addThemeName("primary");

        HorizontalLayout controls = new HorizontalLayout(caseBox, noiseBox, seedField, ask);
        controls.setDefaultVerticalComponentAlignment(Alignment.END);
        controls.setWrap(true);

        arms.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        arms.getStyle().set("gap", "1rem");
        add(controls, new Paragraph("The pipeline calls Claude and Jev for real. An escalated question waits in Escalations."),
                arms);
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
        arms.add(card("Jena (arbiter)", values(jena), "SPARQL over the clean dataset"));
        arms.add(recorded("Claude with skill", RecordedLlmAnswers.withSkill(experimentCase), jena));
        arms.add(recorded("Claude without skill", RecordedLlmAnswers.withoutSkill(experimentCase), jena));
        try {
            GardenPipeline.Run run = pipeline.start(new Question(experimentCase.id(), experimentCase.question()), noise,
                    currentUser.runContext());
            arms.add(run.answer() != null ? pipelineCard(run.answer(), jena)
                    : card("Pipeline", "Waiting for a human",
                    "Escalated: " + run.pending().decision().reason() + ". Answer it in Escalations."));
        } catch (RuntimeException ex) {
            Notification.show("Pipeline failed: " + ex.getMessage());
            arms.add(card("Pipeline", "Failed", ex.getMessage()));
        }
    }

    private static Div recorded(String title, Optional<List<String>> answer, List<String> jena) {
        return answer.map(a -> card(title, values(a), matchLabel(a, jena) + " · recorded by hand"))
                .orElseGet(() -> card(title, "Not recorded", "No hand-recorded session for this case yet"));
    }

    private static Div pipelineCard(PipelineAnswer answer, List<String> jena) {
        String status = answer.answeredByHuman() ? "answered by " + answer.reviewer()
                : answer.escalated() ? "escalated" : "not escalated";
        return card("Pipeline", values(answer.answer()),
                matchLabel(answer.answer(), jena) + " · confidence %.2f · %s".formatted(answer.confidence(), status),
                answer.text());
    }

    private static String matchLabel(List<String> answer, List<String> jena) {
        return new HashSet<>(answer).equals(new HashSet<>(jena)) ? "matches Jena" : "differs from Jena";
    }

    private static String values(List<String> values) {
        return values.isEmpty() ? "∅ (nothing declared)" : String.join(", ", values);
    }

    private static Div card(String title, String value, String... details) {
        Div card = new Div(new H3(title), new Span(value));
        for (String detail : details) {
            Paragraph p = new Paragraph(detail);
            p.getStyle().set("font-size", "0.85rem").set("margin", "0.4rem 0 0");
            card.add(p);
        }
        card.getStyle().set("border", "1px solid var(--vaadin-border-color, #ccc)")
                .set("border-radius", "8px").set("padding", "0.8rem 1rem").set("width", "15rem");
        return card;
    }
}
