package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.theme.lumo.LumoUtility.Display;
import com.vaadin.flow.theme.lumo.LumoUtility.FlexWrap;
import com.vaadin.flow.theme.lumo.LumoUtility.FontSize;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import com.vaadin.flow.theme.lumo.LumoUtility.TextColor;
import app.rootology.garden.GardenService;
import app.rootology.history.PipelineRunRecord;
import app.rootology.history.RunHistory;
import jakarta.annotation.security.PermitAll;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The latest finished questions of the page's context, stored in Postgres: one garden's, or the space's
 * questions without a garden (#56). For the gardener (#80): when, the question, the answer's values, who
 * asked, the outcome and the context, and a click on a row shows the full answer. The quality suite's own
 * details (case, noise, arbiter, match) live only in Compare, for QA.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/history", layout = MainLayout.class)
@RouteAlias(value = "spaces/:spaceId/history", layout = MainLayout.class)
@PermitAll
public class HistoryView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private final RunHistory history;
    private final GardenService gardens;
    private final CurrentUser currentUser;
    private final H3 title = new H3(getTranslation("history.title"));
    private final Paragraph place = new Paragraph();
    private final Grid<PipelineRunRecord> grid = new Grid<>();
    /** The same rows as cards on a phone (#88), where a table's question column has no room. */
    private final Div cards = new Div();
    /** What the page says when there is no question yet (#80, #81), and the way to ask one. */
    private final EmptyState empty = new EmptyState(getTranslation("history.empty"),
            getTranslation("history.empty.ask"), this::openAsk).drawing(EmptyState.Drawing.NOTEBOOK);
    /** The page's own context (#56), read from its route; 0 garden at the space level. */
    private long spaceId;
    private long gardenId;

    public HistoryView(RunHistory history, GardenService gardens, CurrentUser currentUser) {
        this.history = history;
        this.gardens = gardens;
        this.currentUser = currentUser;

        place.addClassNames(FontSize.SMALL, TextColor.SECONDARY);
        grid.addComponentColumn(r -> DateText.dateTimeSpan(r.getFinishedAt(), this)).setHeader(getTranslation("history.date")).setAutoWidth(true);
        grid.addColumn(PipelineRunRecord::getQuestion).setHeader(getTranslation("history.question")).setFlexGrow(2);
        grid.addComponentColumn(this::values).setHeader(getTranslation("history.answer")).setFlexGrow(1);
        grid.addColumn(PipelineRunRecord::getStartedBy).setHeader(getTranslation("history.askedby")).setAutoWidth(true);
        grid.addComponentColumn(this::outcome).setHeader(getTranslation("history.outcome")).setAutoWidth(true);
        grid.addColumn(r -> history.contextOf(r).map(context -> ContextText.line(context, this)).orElse(""))
                .setHeader(getTranslation("history.context")).setFlexGrow(2);
        grid.addItemClickListener(e -> show(e.getItem()));
        grid.addClassName("rootology-clickable-rows");
        grid.addClassName("rootology-history-grid");
        grid.setSizeFull();
        cards.addClassName("rootology-history-cards");

        empty.setVisible(false);
        // A guest only looks (#33): the button is shown once beforeEnter knows the space's role.
        empty.actionVisible(false);

        add(title, place, grid, cards, empty);
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
        String spaceName = currentUser.spaces().stream().filter(s -> s.id() == spaceId)
                .map(app.rootology.spaces.SpaceSummary::name).findFirst().orElse("");
        place.setText(gardenId > 0
                ? gardens.garden(spaceId, currentUser.login(), gardenId)
                .map(g -> getTranslation("context.place.garden", spaceName, g.name())).orElse(spaceName)
                : getTranslation("context.place.nogarden", spaceName));
        empty.actionVisible(currentUser.canContribute(spaceId));
        refresh();
    }

    private void refresh() {
        // Refreshed when the page opens (#80): there is no Refresh button to press.
        List<PipelineRunRecord> runs = spaceId == 0 ? List.of() : history.recent(spaceId, gardenId, currentUser.login());
        grid.setItems(runs);
        grid.setVisible(!runs.isEmpty());
        cards.removeAll();
        runs.forEach(run -> cards.add(card(run)));
        cards.setVisible(!runs.isEmpty());
        empty.setVisible(runs.isEmpty());
    }

    /** The answer's values as small badges, and nothing when the question was refused (#53). */
    private Div values(PipelineRunRecord run) {
        Div badges = new Div();
        badges.addClassNames(Display.FLEX, FlexWrap.WRAP, Gap.XSMALL);
        if (run.isOffDomain()) {
            return badges;
        }
        run.getAnswer().forEach(value -> {
            Badge badge = new Badge(value);
            badge.addThemeVariants(BadgeVariant.SMALL);
            badges.add(badge);
        });
        return badges;
    }

    /** Answered, waiting for a person or off domain (#53): an icon whose tooltip and aria label say the word. */
    private Component outcome(PipelineRunRecord run) {
        VaadinIcon icon = run.isOffDomain() ? VaadinIcon.INFO_CIRCLE_O
                : run.isDecisionEscalated() && !run.isAnsweredByHuman() ? VaadinIcon.HOURGLASS
                : VaadinIcon.CHECK_CIRCLE_O;
        String word = run.isOffDomain() ? getTranslation("history.offdomain")
                : run.isDecisionEscalated() && !run.isAnsweredByHuman() ? getTranslation("history.escalated")
                : getTranslation("history.answered");
        Icon described = icon.create();
        if (run.isOffDomain()) {
            described.addClassNames(TextColor.SECONDARY);
        } else if (run.isDecisionEscalated() && !run.isAnsweredByHuman()) {
            described.addClassNames(TextColor.WARNING);
        } else {
            described.addClassNames(TextColor.SUCCESS);
        }
        described.getElement().setAttribute("role", "img");
        described.getElement().setAttribute("aria-label", word);
        described.setTooltipText(word);
        return described;
    }

    /**
     * One question as a card for a phone (#88): the date and its outcome, the question on as many lines as it
     * needs, the answer's values and who asked. The same click opens the full answer.
     */
    private Component card(PipelineRunRecord run) {
        Div card = new Div();
        card.addClassName("rootology-history-card");
        Div head = new Div(DateText.dateTimeSpan(run.getFinishedAt(), this), outcome(run));
        head.addClassName("rootology-history-card-head");
        Span question = new Span(run.getQuestion());
        question.addClassName("rootology-history-card-question");
        Div badges = values(run);
        badges.addClassName("rootology-history-card-values");
        Span askedBy = new Span(getTranslation("history.askedby") + " " + run.getStartedBy());
        askedBy.addClassNames(FontSize.XSMALL, TextColor.SECONDARY);
        card.add(head, question, badges, askedBy);
        history.contextOf(run).ifPresent(context -> {
            Span line = new Span(ContextText.line(context, this));
            line.addClassNames(FontSize.XSMALL, TextColor.SECONDARY, "rootology-history-card-context");
            card.add(line);
        });
        Button open = RowActions.open(getTranslation("history.open"), e -> show(run));
        open.addClassName("rootology-history-card-open");
        card.add(open);
        return card;
    }

    /** A row opens the full answer of the question (#80), with the context it was given in. */
    private void show(PipelineRunRecord run) {
        Dialog dialog = new Dialog(run.getQuestion());
        dialog.setWidth("36rem");
        dialog.setMaxWidth("100%");
        AnswerCard card = new AnswerCard();
        history.contextOf(run).ifPresent(card::context);
        if (run.isOffDomain()) {
            card.refused(run.getAnswerText() == null ? getTranslation("ask.offdomain") : run.getAnswerText());
        } else if (run.isDecisionEscalated() && !run.isAnsweredByHuman()) {
            card.waiting(ReasonText.text(run.getDecisionReason(), this));
        } else {
            card.answered(run.getAnswerText() == null ? "" : run.getAnswerText(), run.getAnswer(),
                    run.getDecisionConfidence());
        }
        dialog.add(card);
        dialog.getFooter().add(new Button(getTranslation("common.close"), e -> dialog.close()));
        dialog.open();
    }

    /** The Ask page of the same context: the same garden, or the space level like this page. */
    private void openAsk() {
        Map<String, String> parameters = gardenId > 0
                ? Map.of(Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId))
                : Map.of(Routed.SPACE, String.valueOf(spaceId));
        UI.getCurrent().navigate(AskView.class, new RouteParameters(parameters));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("history.title") + " · Rootology";
    }
}
