package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline.PendingReview;
import io.github.vincenzofiore.rootology.pipeline.HumanAnswer;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;

import java.util.Arrays;
import java.util.List;

/** Escalated questions of the current space whose agent is paused. A member answers one and the agent finishes. */
@Route(value = "escalations", layout = MainLayout.class)
@PageTitle("Escalations · Rootology")
@PermitAll
public class EscalationView extends VerticalLayout {

    private final GardenPipeline pipeline;
    private final CurrentUser currentUser;
    private final Grid<PendingReview> grid = new Grid<>();
    private final TextField values = new TextField("Answer values (comma-separated, empty for none)");
    private final TextField note = new TextField("Note");
    private final Button submit = new Button("Answer and resume the agent");

    public EscalationView(GardenPipeline pipeline, CurrentUser currentUser) {
        this.pipeline = pipeline;
        this.currentUser = currentUser;

        grid.addColumn(r -> r.question().id()).setHeader("Case").setAutoWidth(true);
        grid.addColumn(r -> r.question().text()).setHeader("Question").setFlexGrow(2);
        grid.addColumn(r -> r.decision().reason()).setHeader("Why escalated").setFlexGrow(2);
        grid.addColumn(r -> String.join(", ", r.decision().answer())).setHeader("Proposed values");
        grid.addColumn(r -> "%.2f".formatted(r.decision().confidence())).setHeader("Confidence").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.asSingleSelect().addValueChangeListener(e -> {
            boolean selected = e.getValue() != null;
            submit.setEnabled(selected);
            values.setValue(selected ? String.join(", ", e.getValue().decision().answer()) : "");
        });

        values.setWidth("24rem");
        note.setWidth("24rem");
        submit.addThemeName("primary");
        submit.setEnabled(false);
        submit.addClickListener(e -> answerSelected());
        Button refresh = new Button("Refresh", e -> refresh());

        HorizontalLayout form = new HorizontalLayout(values, note, submit, refresh);
        form.setDefaultVerticalComponentAlignment(Alignment.END);
        form.setWrap(true);
        add(new Paragraph("Each row is a paused agent. Pick one, give the answer, and the agent resumes."), grid, form);
        refresh();
    }

    private void answerSelected() {
        PendingReview review = grid.asSingleSelect().getValue();
        if (review == null) {
            return;
        }
        List<String> answer = Arrays.stream(values.getValue().split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).toList();
        try {
            PipelineAnswer result = pipeline.respond(review.processId(), currentUser.space().id(),
                    new HumanAnswer(answer, note.getValue(), currentUser.login()));
            Notification.show(review.question().id() + ": " + result.text(), 8000, Notification.Position.BOTTOM_START);
        } catch (RuntimeException ex) {
            Notification.show("Could not resume the agent: " + ex.getMessage());
        }
        note.clear();
        refresh();
    }

    private void refresh() {
        grid.setItems(pipeline.pending(currentUser.space().id()));
    }
}
