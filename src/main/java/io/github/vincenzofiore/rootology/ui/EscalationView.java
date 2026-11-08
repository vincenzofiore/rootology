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
import com.vaadin.flow.router.RouteAlias;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline.PendingReview;
import io.github.vincenzofiore.rootology.pipeline.HumanAnswer;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;
import io.github.vincenzofiore.rootology.resilience.ServiceUnavailableException;
import jakarta.annotation.security.PermitAll;

import java.util.Arrays;
import java.util.List;

/**
 * Escalated questions of the page's context whose agent is paused: one garden's, or the space's questions
 * without a garden (#56). A member answers one and the agent finishes.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/escalations", layout = MainLayout.class)
@RouteAlias(value = "spaces/:spaceId/escalations", layout = MainLayout.class)
@PermitAll
public class EscalationView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private final GardenPipeline pipeline;
    private final GardenService gardens;
    private final CurrentUser currentUser;
    private final Grid<PendingReview> grid = new Grid<>();
    private final TextField values = new TextField();
    private final TextField note = new TextField();
    private final Button submit = new Button();
    private final Paragraph intro = new Paragraph();
    private final HorizontalLayout form = new HorizontalLayout();
    /** The page's own context (#56), read from its route; 0 garden at the space level. */
    private long spaceId;
    private long gardenId;

    public EscalationView(GardenPipeline pipeline, GardenService gardens, CurrentUser currentUser) {
        this.pipeline = pipeline;
        this.gardens = gardens;
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

        form.add(values, note, submit, refresh);
        form.setDefaultVerticalComponentAlignment(Alignment.END);
        form.setWrap(true);
        // A guest sees the open questions but does not answer them (#33); the role is known in beforeEnter.
        form.setVisible(false);
        add(new H3(getTranslation("escalations.title")), intro, grid, form);
    }

    private void answerSelected() {
        PendingReview review = grid.asSingleSelect().getValue();
        if (review == null) {
            return;
        }
        List<String> answer = Arrays.stream(values.getValue().split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).toList();
        try {
            PipelineAnswer result = pipeline.respond(review.processId(), spaceId, gardenId,
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

    /** The page works in its route's garden, or at the space level: a space or garden the person cannot see is not found. */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        gardenId = where.get().gardenId();
        boolean contributor = currentUser.canContribute(spaceId);
        form.setVisible(contributor);
        intro.setText(getTranslation(contributor ? "escalations.intro" : "escalations.intro.guest"));
        refresh();
    }

    private void refresh() {
        grid.setItems(spaceId == 0 ? List.of() : pipeline.pending(spaceId, gardenId, currentUser.login()));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("escalations.title") + " · Rootology";
    }
}
