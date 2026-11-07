package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.history.PipelineRunRecord;
import io.github.vincenzofiore.rootology.history.RunHistory;
import io.github.vincenzofiore.rootology.roles.AppRole;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The latest finished pipeline runs of the current space, stored in Postgres; QA also sees the arbiter's answer.
 * A link from a notification (#51) names its space with {@code ?space=<id>}, since the current one may be another.
 */
@Route(value = "history", layout = MainLayout.class)
@PermitAll
public class HistoryView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** The space a notification's link opens, when it is not the session's current one (#51). */
    static final String SPACE = "space";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final RunHistory history;
    private final CurrentUser currentUser;
    private final Grid<PipelineRunRecord> grid = new Grid<>();

    public HistoryView(RunHistory history, CurrentUser currentUser) {
        this.history = history;
        this.currentUser = currentUser;

        grid.addColumn(r -> TIME.format(r.getFinishedAt())).setHeader(getTranslation("history.finished")).setAutoWidth(true);
        // Suite columns (case, noise, arbiter) are for QA (#32); everyone sees their questions and answers.
        boolean qa = currentUser.hasRole(AppRole.QA);
        grid.addColumn(PipelineRunRecord::getQuestionId).setHeader(getTranslation("history.case")).setAutoWidth(true)
                .setVisible(qa);
        grid.addColumn(PipelineRunRecord::getStartedBy).setHeader(getTranslation("history.askedby")).setAutoWidth(true);
        grid.addColumn(PipelineRunRecord::getQuestion).setHeader(getTranslation("history.question")).setFlexGrow(2);
        grid.addColumn(r -> r.isNoisy() ? getTranslation("history.noisy", r.getNoiseSeed()) : getTranslation("history.clean"))
                .setHeader(getTranslation("history.input")).setAutoWidth(true).setVisible(qa);
        grid.addColumn(r -> values(r.getAnswer())).setHeader(getTranslation("history.answer")).setFlexGrow(1);
        grid.addColumn(r -> r.getJenaAnswer() == null ? "-" : values(r.getJenaAnswer())).setHeader("Jena").setFlexGrow(1)
                .setVisible(qa);
        grid.addColumn(r -> r.getMatchesJena() == null ? "-" : getTranslation(r.getMatchesJena() ? "common.yes" : "common.no"))
                .setHeader(getTranslation("history.matches")).setAutoWidth(true).setVisible(qa);
        grid.addColumn(r -> "%.2f".formatted(r.getDecisionConfidence())).setHeader(getTranslation("history.confidence"))
                .setAutoWidth(true);
        grid.addColumn(r -> r.isAnsweredByHuman() ? getTranslation("history.byhuman", r.getReviewer())
                : r.isDecisionEscalated() ? getTranslation("history.escalated") : "-")
                .setHeader(getTranslation("history.person")).setAutoWidth(true);
        grid.setSizeFull();
        refresh();

        add(new Button(getTranslation("common.refresh"), e -> refresh()), grid);
        setSizeFull();
    }

    /**
     * Opens the space named in the link. A malformed id or a space the person does not belong to is ignored:
     * the current space is shown, never an error page and never another space's history.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        long id = event.getLocation().getQueryParameters().getSingleParameter(SPACE)
                .map(HistoryView::id).orElse(-1L);
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
        grid.setItems(history.recent(currentUser.space().id(), currentUser.login()));
    }

    private static String values(List<String> values) {
        return values.isEmpty() ? "∅" : String.join(", ", values);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("history.title") + " · Rootology";
    }
}
