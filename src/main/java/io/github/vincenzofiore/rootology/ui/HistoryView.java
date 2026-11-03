package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.history.PipelineRunRecord;
import io.github.vincenzofiore.rootology.history.RunHistory;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** The latest finished pipeline runs of the current space, stored in Postgres, next to the arbiter's answer. */
@Route(value = "history", layout = MainLayout.class)
@PageTitle("History · Rootology")
@PermitAll
public class HistoryView extends VerticalLayout {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public HistoryView(RunHistory history, CurrentUser currentUser) {
        long spaceId = currentUser.space().id();
        Grid<PipelineRunRecord> grid = new Grid<>();
        grid.addColumn(r -> TIME.format(r.getFinishedAt())).setHeader("Finished").setAutoWidth(true);
        grid.addColumn(PipelineRunRecord::getQuestionId).setHeader("Case").setAutoWidth(true);
        grid.addColumn(PipelineRunRecord::getStartedBy).setHeader("Asked by").setAutoWidth(true);
        grid.addColumn(r -> r.isNoisy() ? "noisy, seed " + r.getNoiseSeed() : "clean").setHeader("Input").setAutoWidth(true);
        grid.addColumn(r -> values(r.getAnswer())).setHeader("Pipeline").setFlexGrow(1);
        grid.addColumn(r -> r.getJenaAnswer() == null ? "-" : values(r.getJenaAnswer())).setHeader("Jena").setFlexGrow(1);
        grid.addColumn(r -> r.getMatchesJena() == null ? "-" : r.getMatchesJena() ? "yes" : "no")
                .setHeader("Matches").setAutoWidth(true);
        grid.addColumn(r -> "%.2f".formatted(r.getDecisionConfidence())).setHeader("Confidence").setAutoWidth(true);
        grid.addColumn(r -> r.isAnsweredByHuman() ? "answered by " + r.getReviewer()
                : r.isDecisionEscalated() ? "escalated" : "-").setHeader("Escalation").setAutoWidth(true);
        grid.setItems(history.recent(spaceId));
        grid.setSizeFull();

        add(new Button("Refresh", e -> grid.setItems(history.recent(spaceId))), grid);
        setSizeFull();
    }

    private static String values(List<String> values) {
        return values.isEmpty() ? "∅" : String.join(", ", values);
    }
}
