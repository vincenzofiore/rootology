package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.history.PipelineRunRecord;
import io.github.vincenzofiore.rootology.history.RunHistory;
import io.github.vincenzofiore.rootology.roles.AppRole;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The latest finished pipeline runs of the page's context, stored in Postgres: one garden's, or the space's
 * questions without a garden (#56). QA also sees the arbiter's answer.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/history", layout = MainLayout.class)
@RouteAlias(value = "spaces/:spaceId/history", layout = MainLayout.class)
@PermitAll
public class HistoryView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final RunHistory history;
    private final GardenService gardens;
    private final CurrentUser currentUser;
    private final Grid<PipelineRunRecord> grid = new Grid<>();
    /** The page's own context (#56), read from its route; 0 garden at the space level. */
    private long spaceId;
    private long gardenId;

    public HistoryView(RunHistory history, GardenService gardens, CurrentUser currentUser) {
        this.history = history;
        this.gardens = gardens;
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

        add(new Button(getTranslation("common.refresh"), e -> refresh()), grid);
        setSizeFull();
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
        refresh();
    }

    private void refresh() {
        grid.setItems(spaceId == 0 ? List.of() : history.recent(spaceId, gardenId, currentUser.login()));
    }

    private static String values(List<String> values) {
        return values.isEmpty() ? "∅" : String.join(", ", values);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("history.title") + " · Rootology";
    }
}
