package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.theme.lumo.LumoUtility.AlignItems;
import com.vaadin.flow.theme.lumo.LumoUtility.FlexWrap;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import com.vaadin.flow.theme.lumo.LumoUtility.JustifyContent;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourceCatalog;
import app.rootology.garden.GardenService;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.GardenPipeline.PendingReview;
import app.rootology.pipeline.HumanAnswer;
import jakarta.annotation.security.PermitAll;

import java.util.List;

/**
 * Escalated questions of the page's context whose agent is paused: one garden's, or the space's questions
 * without a garden (#56). One card per waiting question (#86): a member answers it on the card and the agent
 * finishes; a guest only reads the cards (#33).
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/escalations", layout = MainLayout.class)
@RouteAlias(value = "spaces/:spaceId/escalations", layout = MainLayout.class)
@PermitAll
public class EscalationView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private final GardenPipeline pipeline;
    private final GardenService gardens;
    private final CurrentUser currentUser;
    private final CropCatalog crops;
    private final KnowledgeGraph knowledge;
    private final SourceCatalog sources;
    private final KnowledgeEnrichment enrichment;
    private final Paragraph intro = new Paragraph();
    /** One card per waiting question (#86), in the order they started waiting. */
    private final Div cards = new Div();
    /** What the page shows when nothing waits (#81). */
    private final EmptyState empty = new EmptyState(getTranslation("empty.escalations"));
    private final Button refresh = new Button(getTranslation("escalations.refresh"), e -> refresh());
    private boolean contributor;
    /** The page's own context (#56), read from its route; 0 garden at the space level. */
    private long spaceId;
    private long gardenId;

    public EscalationView(GardenPipeline pipeline, GardenService gardens, CurrentUser currentUser,
                          CropCatalog crops, KnowledgeGraph knowledge, SourceCatalog sources,
                          KnowledgeEnrichment enrichment) {
        this.pipeline = pipeline;
        this.gardens = gardens;
        this.currentUser = currentUser;
        this.crops = crops;
        this.knowledge = knowledge;
        this.sources = sources;
        this.enrichment = enrichment;

        H3 title = new H3(getTranslation("escalations.title"));
        refresh.addThemeName("tertiary");
        HorizontalLayout header = new HorizontalLayout(title, refresh);
        header.setWidthFull();
        header.setWrap(true);
        header.addClassNames(AlignItems.CENTER, JustifyContent.BETWEEN, Gap.MEDIUM, FlexWrap.WRAP);
        cards.addClassName("rootology-cards");
        empty.setVisible(false);
        add(header, intro, empty, cards);
    }

    /** One waiting question as a card: the answer controls only for a member of the space (#33). */
    private WaitingQuestionCard card(PendingReview review, List<String> suggestions) {
        return new WaitingQuestionCard(review, contributor, suggestions,
                (values, note) -> pipeline.respond(review.processId(), spaceId, gardenId,
                        new HumanAnswer(values, note, currentUser.login())),
                answer -> AnswerCards.of(answer, spaceId, currentUser.login(), sources, enrichment, this));
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
        java.util.Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        gardenId = where.get().gardenId();
        contributor = currentUser.canContribute(spaceId);
        intro.setText(getTranslation(contributor ? "escalations.intro" : "escalations.intro.guest"));
        refresh();
    }

    private void refresh() {
        List<PendingReview> waiting = spaceId == 0 ? List.of()
                : pipeline.pending(spaceId, gardenId, currentUser.login());
        List<String> suggestions = waiting.isEmpty() ? List.of() : suggestions();
        cards.removeAll();
        waiting.forEach(review -> cards.add(card(review, suggestions)));
        cards.setVisible(!waiting.isEmpty());
        empty.setVisible(waiting.isEmpty());
        intro.setVisible(!waiting.isEmpty());
    }

    @Override
    public String getPageTitle() {
        return getTranslation("escalations.title") + " · Rootology";
    }
}
