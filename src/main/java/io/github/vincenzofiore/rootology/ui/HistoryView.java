package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.history.PipelineRunRecord;
import io.github.vincenzofiore.rootology.history.RunHistory;
import io.github.vincenzofiore.rootology.roles.AppRole;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** The latest finished pipeline runs of the current space, stored in Postgres; QA also sees the arbiter's answer. */
@Route(value = "history", layout = MainLayout.class)
@PermitAll
public class HistoryView extends VerticalLayout implements HasDynamicTitle {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public HistoryView(RunHistory history, CurrentUser currentUser) {
        long spaceId = currentUser.space().id();
        Grid<PipelineRunRecord> grid = new Grid<>();
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
        grid.setItems(history.recent(spaceId));
        grid.setSizeFull();

        add(new Button(getTranslation("common.refresh"), e -> grid.setItems(history.recent(spaceId))), grid);
        setSizeFull();
    }

    private static String values(List<String> values) {
        return values.isEmpty() ? "∅" : String.join(", ", values);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("history.title") + " · Rootology";
    }
}
