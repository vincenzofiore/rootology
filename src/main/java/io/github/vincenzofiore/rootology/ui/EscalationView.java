package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline.PendingReview;
import io.github.vincenzofiore.rootology.pipeline.HumanAnswer;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;
import io.github.vincenzofiore.rootology.resilience.ServiceUnavailableException;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;

import java.util.Arrays;
import java.util.List;

/**
 * Escalated questions of the current space whose agent is paused. A member answers one and the agent finishes.
 * A link from a notification (#51) names its space with {@code ?space=<id>}, since the current one may be another.
 */
@Route(value = "escalations", layout = MainLayout.class)
@PermitAll
public class EscalationView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** The space a notification's link opens, when it is not the session's current one (#51). */
    static final String SPACE = "space";

    private final GardenPipeline pipeline;
    private final CurrentUser currentUser;
    private final Grid<PendingReview> grid = new Grid<>();
    private final TextField values = new TextField();
    private final TextField note = new TextField();
    private final Button submit = new Button();

    public EscalationView(GardenPipeline pipeline, CurrentUser currentUser) {
        this.pipeline = pipeline;
        this.currentUser = currentUser;
        values.setLabel(getTranslation("escalations.values"));
        note.setLabel(getTranslation("escalations.note"));
        submit.setText(getTranslation("escalations.answer"));

        grid.addColumn(r -> r.question().text()).setHeader(getTranslation("escalations.question")).setFlexGrow(2);
        grid.addColumn(r -> ReasonText.text(r.decision().reason(), this)).setHeader(getTranslation("escalations.why"))
                .setFlexGrow(2);
        grid.addColumn(r -> String.join(", ", r.decision().answer())).setHeader(getTranslation("escalations.proposed"));
        grid.addColumn(r -> "%.2f".formatted(r.decision().confidence())).setHeader(getTranslation("escalations.confidence"))
                .setAutoWidth(true);
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
        Button refresh = new Button(getTranslation("escalations.refresh"), e -> refresh());

        HorizontalLayout form = new HorizontalLayout(values, note, submit, refresh);
        form.setDefaultVerticalComponentAlignment(Alignment.END);
        form.setWrap(true);
        // A guest sees the open questions but does not answer them (#33).
        form.setVisible(currentUser.canContribute());
        add(new H3(getTranslation("escalations.title")), new Paragraph(getTranslation(currentUser.canContribute()
                ? "escalations.intro" : "escalations.intro.guest")), grid, form);
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
            Notification.show(result.text(), 8000, Notification.Position.BOTTOM_START);
        } catch (ServiceUnavailableException ex) {
            Notification.show(getTranslation("error.claude"));
        } catch (RuntimeException ex) {
            Notification.show(getTranslation("escalations.failed", ex.getMessage()));
        }
        note.clear();
        refresh();
    }

    /**
     * Opens the space named in the link. A malformed id or a space the person does not belong to is ignored:
     * the current space is shown, never an error page and never another space's questions.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        long id = event.getLocation().getQueryParameters().getSingleParameter(SPACE)
                .map(EscalationView::id).orElse(-1L);
        if (id <= 0 || id == currentUser.space().id()) {
            return;
        }
        try {
            currentUser.switchTo(id);
        } catch (SpaceAccessException ignored) {
            return;
        }
        refresh();
    }

    private static long id(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void refresh() {
        grid.setItems(pipeline.pending(currentUser.space().id(), currentUser.login()));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("escalations.title") + " · Rootology";
    }
}
