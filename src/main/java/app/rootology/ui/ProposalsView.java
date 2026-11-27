package app.rootology.ui;

import app.rootology.facts.CropCatalog;
import app.rootology.garden.CropProposalRule.Goal;
import app.rootology.garden.CropProposalRule.Proposal;
import app.rootology.garden.CropProposalService;
import app.rootology.garden.CropProposalService.AreaProposals;
import app.rootology.garden.DirectSun;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenTime;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.garden.PlantingMethod;
import app.rootology.decision.Intent;
import app.rootology.decision.QueryKind;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.Question;
import app.rootology.pipeline.RunContext;
import app.rootology.resilience.ServiceUnavailableException;
import app.rootology.usage.UsageLimitException;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.AbstractIcon;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * What to put in the free areas of a garden (#141): one or two goals on top, then a card per free area with
 * the crops of crop-proposal-v1 that Jev is sure of, each with a "Plant" button that asks first. An area Jev
 * is not sure about waits for a person of the space, with no crops shown. Contributors only.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/proposals", layout = MainLayout.class)
@PermitAll
public class ProposalsView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProposalsView.class);

    /** The goal chips, in the order of the sketch, with their icons; the word is the tooltip too. */
    static final Map<Goal, String> GOAL_ICONS = Map.of(Goal.QUICK_HARVEST, "vaadin:stopwatch",
            Goal.VARIETY, "vaadin:grid-small", Goal.BENEFICIALS, "vaadin:bug-o", Goal.SOIL, "icons/areas/ground.svg");
    static final List<Goal> GOAL_ORDER = List.of(Goal.QUICK_HARVEST, Goal.VARIETY, Goal.BENEFICIALS, Goal.SOIL);

    private final CropProposalService proposals;
    private final GardenPipeline pipeline;
    private final GardenService gardens;
    private final CropCatalog crops;
    private final CurrentUser currentUser;
    private final Executor executor;
    private final Clock clock;
    private final PageHeader title = new PageHeader("");
    private final Div goalChips = new Div();
    private final Div areas = new Div();
    private final List<Goal> goals = new ArrayList<>(List.of(Goal.VARIETY));
    /** Raised at every change of goals, so an answer for the old goals is dropped when it arrives. */
    private int generation;
    private long spaceId;
    private long gardenId;
    private Month month;

    public ProposalsView(CropProposalService proposals, GardenPipeline pipeline, GardenService gardens,
                         CropCatalog crops, CurrentUser currentUser,
                         @Qualifier("applicationTaskExecutor") Executor executor, Clock clock) {
        this.proposals = proposals;
        this.pipeline = pipeline;
        this.gardens = gardens;
        this.crops = crops;
        this.currentUser = currentUser;
        this.executor = executor;
        this.clock = clock;
        title.setHeading(getTranslation("proposals.title"));
        goalChips.addClassName("rootology-goal-chips");
        areas.addClassName("rootology-proposal-areas");
        addClassName("rootology-proposals");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        gardenId = where.get().gardenId();
        removeAll();
        if (!currentUser.canContribute(spaceId)) {
            // A guest only looks (#33): a proposal is a question, and guests ask none.
            add(title, new EmptyState(getTranslation("proposals.guest")));
            return;
        }
        month = today().getMonth();
        Icon calendar = VaadinIcon.CALENDAR_O.create();
        calendar.getElement().setAttribute("aria-hidden", "true");
        Span when = new Span(calendar, new Span(month.getDisplayName(TextStyle.FULL_STANDALONE, locale())));
        when.addClassNames("rootology-page-place", "rootology-proposal-fact");
        title.besideTitle(when);
        Paragraph hint = new Paragraph(getTranslation("proposals.goals.hint"));
        hint.addClassName("rootology-goal-hint");
        add(title, goalChips, hint, areas);
        chips();
        refresh();
    }

    /** The four goals as toggle chips: one or two on; with two on, the others wait until one is turned off. */
    private void chips() {
        goalChips.removeAll();
        for (Goal goal : GOAL_ORDER) {
            String word = getTranslation("proposals.goal." + goal.name());
            Button chip = new Button(word, icon(GOAL_ICONS.get(goal)));
            boolean on = goals.contains(goal);
            chip.addClassName("rootology-goal-chip");
            chip.addClassName(on ? "rootology-goal-chip-on" : "rootology-goal-chip-off");
            chip.getElement().setAttribute("aria-pressed", String.valueOf(on));
            chip.setTooltipText(getTranslation("proposals.goal." + goal.name() + ".tooltip"));
            chip.setEnabled(on || goals.size() < 2);
            chip.addClickListener(e -> {
                if (goals.contains(goal)) {
                    goals.remove(goal);
                } else {
                    goals.add(goal);
                }
                chips();
                refresh();
            });
            goalChips.add(chip);
        }
    }

    /** The free areas for the chosen goals; each area's crops go to Jev in the background. */
    private void refresh() {
        generation++;
        areas.removeAll();
        if (goals.isEmpty()) {
            return;
        }
        String login = currentUser.login();
        List<AreaProposals> free = proposals.propose(spaceId, login, gardenId, month, List.copyOf(goals)).areas();
        if (free.isEmpty()) {
            areas.add(new EmptyState(getTranslation("proposals.none")));
            return;
        }
        Map<Long, PlotSummary> plots = new java.util.HashMap<>();
        gardens.plots(spaceId, login, gardenId).forEach(p -> plots.put(p.id(), p));
        for (AreaProposals area : free) {
            Div card = areaCard(plots.get(area.areaId()), area);
            areas.add(card);
            if (area.proposals().isEmpty()) {
                card.add(paragraph(getTranslation("proposals.area.nothing")));
            } else {
                judge(card, area);
            }
        }
    }

    /** The area's card: its name, its light this month and its size, before any crop. */
    private Div areaCard(PlotSummary plot, AreaProposals area) {
        H3 name = new H3(area.areaName());
        name.addClassName("rootology-proposal-area-name");
        Div facts = new Div();
        facts.addClassName("rootology-proposal-area-facts");
        DirectSun sun = plot == null ? null : plot.directSunIn(month);
        if (sun != null) {
            facts.add(labelled(AreasView.sunIcon(sun).create(), getTranslation("directsun." + sun.name())));
        }
        if (plot != null && plot.areaM2() != null) {
            facts.add(new Span(getTranslation("proposals.area.size", plot.areaM2().stripTrailingZeros().toPlainString())));
        }
        Div head = new Div(new Div(name, facts));
        head.addClassName("rootology-proposal-area-head");
        Div card = new Div(head);
        card.addClassName("rootology-proposal-area");
        return card;
    }

    /** One question per area, built here with its intent (#141): Jev judges the crops, Claude only phrases. */
    private void judge(Div card, AreaProposals area) {
        ProgressBar thinking = new ProgressBar();
        thinking.setIndeterminate(true);
        thinking.addClassName("rootology-proposal-thinking");
        thinking.getElement().setAttribute("aria-label", getTranslation("proposals.area.thinking"));
        card.add(thinking);
        String language = locale().getLanguage();
        String goalWords = String.join(", ", goals.stream()
                .map(g -> getTranslation("proposals.goal." + g.name()).toLowerCase(locale())).toList());
        String text = getTranslation("proposals.question", area.areaName(),
                month.getDisplayName(TextStyle.FULL_STANDALONE, locale()), goalWords);
        Intent intent = new Intent(QueryKind.PROPOSE_IN_AREA, area.areaName(), null, language, null, month.name(),
                null, null, goals.stream().map(Goal::name).toList());
        RunContext context = currentUser.runContext(spaceId, gardenId);
        int asked = generation;
        UI ui = UI.getCurrent();
        executor.execute(() -> {
            try {
                GardenPipeline.Run run = pipeline.propose(new Question("proposal", text), intent, context);
                update(ui, asked, () -> judged(card, thinking, area, run, language));
            } catch (UsageLimitException ex) {
                update(ui, asked, () -> failed(card, thinking, getTranslation("ask.limit", ex.limit())));
            } catch (ServiceUnavailableException ex) {
                update(ui, asked, () -> failed(card, thinking, getTranslation("error.claude")));
            } catch (RuntimeException ex) {
                update(ui, asked, () -> failed(card, thinking, UiErrors.text(this, ex)));
            }
        });
    }

    private void judged(Div card, ProgressBar thinking, AreaProposals area, GardenPipeline.Run run, String language) {
        card.remove(thinking);
        if (run.answer() == null) {
            // Jev is not sure: the area waits for a person, with no crop shown (#141), as any waiting question.
            card.addClassName("rootology-proposal-area-waiting");
            Icon hourglass = VaadinIcon.HOURGLASS.create();
            hourglass.addClassName("rootology-waiting-icon");
            hourglass.getElement().setAttribute("aria-hidden", "true");
            RouterLink waiting = new RouterLink("", AskView.class, new RouteParameters(Map.of(
                    Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId))));
            waiting.setQueryParameters(QueryParameters.simple(Map.of(AskView.WAITING, "")));
            waiting.add(new Span(getTranslation("proposals.area.waiting.open")));
            Div line = new Div(hourglass, new Span(getTranslation("proposals.area.waiting")), waiting);
            line.addClassName("rootology-proposal-waiting");
            card.add(line);
            return;
        }
        ProgressBar bar = new ProgressBar(0, 1, Math.max(0, Math.min(1, run.answer().confidence())));
        bar.addClassName("rootology-proposal-confidence");
        String sure = getTranslation(AnswerCard.confidenceKey(run.answer().confidence()));
        bar.getElement().setAttribute("aria-label", sure);
        bar.getElement().setAttribute("title", sure);
        if (run.answer().confidence() >= 0.80) {
            bar.addThemeName("success");
        }
        card.getChildren().findFirst().ifPresent(head -> ((Div) head).add(bar));
        List<Proposal> sureOnes = area.proposals().stream()
                .filter(p -> run.answer().answer().contains(name(p.cropId(), language))).toList();
        if (sureOnes.isEmpty()) {
            card.add(paragraph(getTranslation("proposals.area.nothing")));
        }
        sureOnes.forEach(p -> card.add(cropRow(card, area, p, language)));
    }

    /** One crop: its name, how and when it goes in, the goals it meets, a question mark if something is unknown. */
    private Div cropRow(Div card, AreaProposals area, Proposal proposal, String language) {
        String cropName = name(proposal.cropId(), language);
        Span name = new Span(cropName);
        name.addClassName("rootology-proposal-crop-name");
        Div icons = new Div();
        icons.addClassName("rootology-proposal-crop-facts");
        String monthWord = Month.of(proposal.month()).getDisplayName(TextStyle.SHORT_STANDALONE, locale());
        String activity = getTranslation("activity." + proposal.activity());
        Span when = labelled(icon("SOW".equals(proposal.activity()) ? GardenHomeView.SPROUT : "vaadin:arrow-circle-down-o"),
                activity + " · " + Month.of(proposal.month()).getDisplayName(TextStyle.FULL_STANDALONE, locale()));
        when.add(new Span(monthWord));
        icons.add(when);
        GOAL_ORDER.stream().filter(proposal.goals()::contains).forEach(g -> icons.add(
                labelled(icon(GOAL_ICONS.get(g)), getTranslation("proposals.goal." + g.name()))));
        icons.add(labelled(VaadinIcon.REFRESH.create(), getTranslation("proposals.rotation")));
        if (!proposal.uncertainties().isEmpty()) {
            String unknown = String.join(" · ", proposal.uncertainties().stream()
                    .map(u -> getTranslation("proposals.uncertainty." + u.name())).toList());
            Span mark = labelled(VaadinIcon.QUESTION_CIRCLE_O.create(), unknown);
            mark.addClassName("rootology-proposal-unknown");
            icons.add(mark);
        }
        Button plant = new Button(getTranslation("proposals.plant"), VaadinIcon.PLUS.create(),
                e -> confirmPlant(card, area, proposal, cropName));
        plant.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        plant.setAriaLabel(getTranslation("proposals.plant.label", cropName, area.areaName()));
        Div row = new Div(new Div(name, icons), plant);
        row.addClassName("rootology-proposal-crop");
        return row;
    }

    /** "Plant" asks first; then the planting is recorded today and the area leaves the list at once. */
    private void confirmPlant(Div card, AreaProposals area, Proposal proposal, String cropName) {
        PlantingMethod method = "SOW".equals(proposal.activity()) ? PlantingMethod.SOWN : PlantingMethod.TRANSPLANTED;
        Dialog dialog = new Dialog(getTranslation("proposals.plant.title", cropName, area.areaName()));
        dialog.add(new Paragraph(getTranslation(method == PlantingMethod.SOWN ? "proposals.plant.sown"
                : "proposals.plant.transplanted")));
        Button confirm = new Button(getTranslation("proposals.plant"), e -> {
            dialog.close();
            gardens.plant(spaceId, currentUser.login(), area.areaId(), proposal.cropId(), method, today(), null);
            areas.remove(card);
            Toasts.done(getTranslation("proposals.planted", cropName, area.areaName()));
            if (areas.getChildren().findAny().isEmpty()) {
                areas.add(new EmptyState(getTranslation("proposals.none")));
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()), confirm);
        dialog.open();
    }

    private void failed(Div card, ProgressBar thinking, String text) {
        card.remove(thinking);
        card.add(paragraph(text));
    }

    /** A change from the background thread, dropped if the page is gone or the goals changed meanwhile. */
    private void update(UI ui, int asked, Runnable change) {
        if (ui == null || ui.isClosing()) {
            return;
        }
        try {
            ui.access(() -> {
                if (isAttached() && asked == generation) {
                    change.run();
                }
            });
        } catch (com.vaadin.flow.component.UIDetachedException | IllegalStateException detached) {
            // The visit ended while Jev judged: nothing to show, the run is in the history anyway.
            log.debug("Proposal finished after the page closed");
        }
    }

    private String name(String cropId, String language) {
        return crops.byId(cropId).map(c -> "it".equals(language) ? c.italianName() : c.englishName()).orElse(cropId);
    }

    /** Today at the garden of the page (#158): the month the proposals are for. */
    private LocalDate today() {
        GardenSummary garden = gardenId == 0 ? null
                : gardens.garden(spaceId, currentUser.login(), gardenId).orElse(null);
        return GardenTime.today(garden, clock);
    }

    private Locale locale() {
        return NoteText.locale(this);
    }

    /** A Vaadin icon ("vaadin:name") or one of the app's drawings (a path). */
    private static AbstractIcon<?> icon(String name) {
        return name.startsWith("vaadin:") ? new Icon(name) : new SvgIcon(name);
    }

    /** An icon whose word is its tooltip and what a screen reader says. */
    private static Span labelled(AbstractIcon<?> icon, String word) {
        icon.getElement().setAttribute("aria-hidden", "true");
        Span span = new Span(icon);
        span.addClassName("rootology-proposal-fact");
        span.getElement().setAttribute("title", word);
        span.getElement().setAttribute("aria-label", word);
        span.getElement().setAttribute("role", "img");
        return span;
    }

    private static Paragraph paragraph(String text) {
        Paragraph paragraph = new Paragraph(text);
        paragraph.addClassName("rootology-card-text");
        return paragraph;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("proposals.title") + " · Rootology";
    }
}
