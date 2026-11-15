package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourceCatalog;
import app.rootology.garden.GardenService;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.GardenPipeline.PendingReview;
import app.rootology.pipeline.HumanAnswer;
import app.rootology.pipeline.PipelineAnswer;
import jakarta.annotation.security.PermitAll;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Escalated questions of the page's context whose agent is paused: every garden's and those without one at
 * the space level (#109), one garden's inside it. One card per waiting question (#86): a member answers it
 * on the card and the agent finishes, in the garden the question was asked in. Only for whoever can answer
 * (#104): a guest is sent to Ask's history, where the waiting questions are listed too. The list updates by
 * itself when a question starts waiting or is answered, like the bell (#109): no refresh button.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/escalations", layout = MainLayout.class)
@RouteAlias(value = "spaces/:spaceId/escalations", layout = MainLayout.class)
@PermitAll
public class EscalationView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** The process id of a waiting question's card, so a live update leaves the other cards alone (#109). */
    private static final String PROCESS = "data-process";

    private final GardenPipeline pipeline;
    private final GardenService gardens;
    private final CurrentUser currentUser;
    private final CropCatalog crops;
    private final KnowledgeGraph knowledge;
    private final SourceCatalog sources;
    private final KnowledgeEnrichment enrichment;
    private final NotificationBroadcaster broadcaster;
    private final Paragraph intro = new Paragraph();
    /** One card per waiting question (#86), newest first; an answer given here keeps its place. */
    private final Div cards = new Div();
    /** What the page shows when nothing waits (#81). */
    private final EmptyState empty = new EmptyState(getTranslation("empty.escalations"))
            .drawing(EmptyState.Drawing.WATERING_CAN);
    private boolean contributor;
    /** The page's own context (#56), read from its route; 0 garden at the space level. */
    private long spaceId;
    private long gardenId;
    /** An answer this page is handing to the pipeline: the update it causes must not rebuild its own card. */
    private boolean answering;
    /** What stops the bell's live updates from reaching this page (#109); null while it is not on screen. */
    private Runnable stopListening;

    public EscalationView(GardenPipeline pipeline, GardenService gardens, CurrentUser currentUser,
                          CropCatalog crops, KnowledgeGraph knowledge, SourceCatalog sources,
                          KnowledgeEnrichment enrichment, NotificationBroadcaster broadcaster) {
        this.pipeline = pipeline;
        this.gardens = gardens;
        this.currentUser = currentUser;
        this.crops = crops;
        this.knowledge = knowledge;
        this.sources = sources;
        this.enrichment = enrichment;
        this.broadcaster = broadcaster;

        PageHeader header = new PageHeader(getTranslation("escalations.title"));
        cards.addClassName("rootology-cards");
        empty.setVisible(false);
        add(header, intro, empty, cards);
    }

    /** One waiting question as a card: the answer controls only for a member of the space (#33). */
    private WaitingQuestionCard card(PendingReview review, List<String> suggestions) {
        WaitingQuestionCard card = new WaitingQuestionCard(review, contributor, suggestions,
                (values, note) -> answer(review, values, note),
                answer -> AnswerCards.of(answer, spaceId, currentUser.login(), sources, enrichment, this),
                gardenId == 0);
        card.getElement().setAttribute(PROCESS, review.processId());
        return card;
    }

    /**
     * Answers in the garden the question was asked in (#109), taken from the waiting question itself: the
     * space level lists its gardens' questions too, so a card must answer in its own context.
     */
    private PipelineAnswer answer(PendingReview review, List<String> values, String note) {
        answering = true;
        try {
            return pipeline.respond(review.processId(), spaceId, review.context().gardenId(),
                    new HumanAnswer(values, note, currentUser.login()));
        } finally {
            answering = false;
        }
    }

    /** The values a person can add: the catalogue's crops and the knowledge base's organisms, in the page's language. */
    private List<String> suggestions() {
        boolean english = NoteText.locale(this).getLanguage().equals("en");
        return WaitingQuestionCard.suggestions(
                crops.all().stream().map(c -> english ? c.englishName() : c.italianName()),
                knowledge.organisms().stream().map(o -> english ? o.englishName() : o.italianName()));
    }

    /** The page works in its route's garden, or at the space level: a space or garden the person cannot see is not found. */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        gardenId = where.get().gardenId();
        contributor = currentUser.canContribute(spaceId);
        if (!contributor) {
            // Waiting questions are for whoever can answer (#104): a guest follows them in Ask's history.
            event.forwardTo(HistoryView.target(String.valueOf(spaceId), gardenId > 0 ? String.valueOf(gardenId) : ""),
                    QueryParameters.simple(Map.of(AskView.HISTORY, "")));
            return;
        }
        intro.setText(getTranslation(gardenId > 0 ? "escalations.intro" : "escalations.intro.space"));
        listen();
        refresh();
    }

    /**
     * Follows the space's waiting questions from here until the page goes away (#109): the same broadcast the
     * bell uses, so a question asked or answered in another browser shows up without a refresh button.
     */
    private void listen() {
        if (stopListening != null) {
            stopListening.run();
        }
        stopListening = broadcaster.register(currentUser.login(), this::changed);
    }

    /** The waiting questions of this page: every garden's at the space level (#109), one garden's inside it. */
    private List<PendingReview> waiting() {
        return spaceId == 0 ? List.of()
                : gardenId > 0 ? pipeline.pending(spaceId, gardenId, currentUser.login())
                : pipeline.pendingInSpace(spaceId, currentUser.login());
    }

    /**
     * Reads the waiting questions again and changes only what changed (#109): a card whose question is no
     * longer waiting goes, a new one comes first (newest first), and an answer given here stays.
     */
    private void refresh() {
        if (answering || spaceId == 0) {
            return;
        }
        List<PendingReview> waiting = waiting();
        Set<String> ids = waiting.stream().map(PendingReview::processId).collect(Collectors.toSet());
        for (Component child : cards.getChildren().toList()) {
            String process = child.getElement().getAttribute(PROCESS);
            if (process != null && !ids.contains(process)) {
                child.removeFromParent();
            }
        }
        Set<String> shown = new HashSet<>();
        for (Component child : cards.getChildren().toList()) {
            String process = child.getElement().getAttribute(PROCESS);
            if (process != null) {
                shown.add(process);
            }
        }
        List<String> suggestions = waiting.isEmpty() ? List.of() : suggestions();
        // Newest first: the questions not shown yet go above the others, in the list's order.
        List<PendingReview> fresh = waiting.stream().filter(r -> !shown.contains(r.processId())).toList();
        for (int i = fresh.size() - 1; i >= 0; i--) {
            cards.addComponentAsFirst(card(fresh.get(i), suggestions));
        }
        boolean any = !cards.getChildren().toList().isEmpty();
        cards.setVisible(any);
        empty.setVisible(!any);
        intro.setVisible(any);
    }

    /** The live update of the list (#109): the change may come from another person's browser. */
    void changed() {
        UI ui = getUI().orElse(null);
        if (ui == null) {
            // Not on screen yet (a page being prepared, or a test): nothing to update later, read it now.
            refresh();
            return;
        }
        ui.access(this::refresh);
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (stopListening != null) {
            stopListening.run();
            stopListening = null;
        }
        super.onDetach(event);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("escalations.title") + " · Rootology";
    }
}
