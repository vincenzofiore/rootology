package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import app.rootology.enrichment.InvalidProposalException;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.enrichment.ProposalScope;
import app.rootology.enrichment.ProposalStatus;
import app.rootology.enrichment.ProposalSummary;
import app.rootology.facts.Contribution;
import app.rootology.garden.GardenService;
import app.rootology.roles.AppRole;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import jakarta.annotation.security.PermitAll;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Knowledge enrichment (#24): propose a missing fact for this space (applies at once) or for everyone
 * (a curator reviews it), see and withdraw this space's proposals, and, for curators, the review queue.
 * The proposals belong to the space, so every garden of the space shows the same ones, and the space level
 * has the page too (#56).
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/knowledge", layout = MainLayout.class)
@RouteAlias(value = "spaces/:spaceId/knowledge", layout = MainLayout.class)
@PermitAll
public class KnowledgeView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    /** A crop or an organism as offered in the forms. */
    record Option(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    static final String ALIAS = "ALIAS";
    static final String PAIR = "PAIR";
    static final String HOST = "HOST";

    private final KnowledgeEnrichment enrichment;
    private final GardenService gardens;
    private final CurrentUser currentUser;
    private final List<Option> crops;
    private final List<Option> organisms;
    private final ProposalText text;
    private final Paragraph askedAbout = new Paragraph();
    private final Grid<ProposalSummary> mine = new Grid<>();


    /** The page's own context (#56), read from its route. */
    private long spaceId;
    /** Proposing and withdrawing need an OWNER or MEMBER (#33): shown once the page knows its space. */
    private final VerticalLayout proposalForm;
    private Grid.Column<ProposalSummary> withdraw;

    public KnowledgeView(KnowledgeEnrichment enrichment, CropCatalog cropCatalog, KnowledgeGraph knowledge,
                         GardenService gardens, CurrentUser currentUser) {
        this.enrichment = enrichment;
        this.gardens = gardens;
        this.currentUser = currentUser;
        this.crops = cropCatalog.all().stream()
                .map(c -> new Option(c.id(), c.italianName() + " (" + c.englishName() + ")")).toList();
        this.organisms = knowledge.organisms().stream()
                .map(o -> new Option(o.id(), o.italianName() + " (" + o.englishName() + ")")).toList();
        this.text = new ProposalText(cropCatalog, knowledge);

        askedAbout.setVisible(false);
        add(new H3(getTranslation("knowledge.title")), new Paragraph(getTranslation("knowledge.intro")),
                askedAbout);
        // A guest only looks (#33): no proposals, no withdrawals; the role is known in beforeEnter.
        proposalForm = form();
        proposalForm.setVisible(false);
        add(proposalForm);

        mine.addColumn(p -> text.describe(this, p)).setHeader(getTranslation("knowledge.fact")).setFlexGrow(3);
        mine.addColumn(p -> getTranslation(p.scope() == ProposalScope.SPACE ? "knowledge.for.space" : "knowledge.for.everyone"))
                .setHeader(getTranslation("knowledge.for"))
                .setAutoWidth(true);
        mine.addColumn(p -> getTranslation("proposal." + p.status().name())).setHeader(getTranslation("knowledge.status"))
                .setAutoWidth(true);
        mine.addColumn(ProposalSummary::proposedBy).setHeader(getTranslation("knowledge.by")).setAutoWidth(true);
        mine.addColumn(this::review).setHeader(getTranslation("knowledge.review")).setFlexGrow(2);
        withdraw = mine.addComponentColumn(p -> {
            Button withdraw = new Button(getTranslation("knowledge.withdraw"), e -> act(() ->
                    enrichment.withdraw(spaceId, currentUser.login(), p.id())));
            withdraw.setVisible(p.status() == ProposalStatus.PENDING
                    || (p.status() == ProposalStatus.APPROVED && p.scope() == ProposalScope.SPACE));
            return withdraw;
        }).setAutoWidth(true);
        withdraw.setVisible(false);
        mine.setAllRowsVisible(true);
        add(new H4(getTranslation("knowledge.mine")), mine);

        refresh();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        boolean contributor = currentUser.canContribute(spaceId);
        proposalForm.setVisible(contributor);
        withdraw.setVisible(contributor);
        refresh();
        // From Ask: the question that could not be answered, as a reminder of what is missing.
        List<String> question = event.getLocation().getQueryParameters().getParameters().getOrDefault("question",
                List.of());
        askedAbout.setVisible(!question.isEmpty());
        if (!question.isEmpty()) {
            askedAbout.setText(getTranslation("knowledge.asked", question.getFirst()));
        }
    }

    private VerticalLayout form() {
        RadioButtonGroup<String> kind = new RadioButtonGroup<>(getTranslation("knowledge.missing"), ALIAS, PAIR, HOST);
        kind.setItemLabelGenerator(k -> getTranslation("knowledge.kind." + k));
        kind.setValue(ALIAS);

        List<Option> named = new ArrayList<>(crops);
        named.addAll(organisms);
        ComboBox<Option> target = combo(getTranslation("knowledge.target"), named);
        TextField name = new TextField(getTranslation("knowledge.othername"));
        RadioButtonGroup<String> language = new RadioButtonGroup<>(getTranslation("knowledge.language"), "it", "en");
        language.setItemLabelGenerator(l -> getTranslation("knowledge.language." + l));
        language.setValue("it");
        HorizontalLayout alias = row(target, name, language);

        ComboBox<Option> crop = combo(getTranslation("garden.crop"), crops);
        ComboBox<Option> partner = combo(getTranslation("knowledge.with"), crops);
        RadioButtonGroup<String> relation = new RadioButtonGroup<>(getTranslation("knowledge.relation"), "COMPANION",
                "ANTAGONIST");
        relation.setItemLabelGenerator(r -> getTranslation("knowledge.relation." + r));
        relation.setValue("COMPANION");
        TextField effect = new TextField(getTranslation("knowledge.effect"));
        effect.setPlaceholder(getTranslation("knowledge.effect.hint"));
        HorizontalLayout pair = row(crop, partner, relation, effect);

        ComboBox<Option> organism = combo(getTranslation("knowledge.organism"), organisms);
        ComboBox<Option> host = combo(getTranslation("knowledge.oncrop"), crops);
        ComboBox<Month> from = months(getTranslation("knowledge.from"));
        ComboBox<Month> to = months(getTranslation("knowledge.to"));
        HorizontalLayout pest = row(organism, host, from, to);

        RadioButtonGroup<ProposalScope> scope = new RadioButtonGroup<>(getTranslation("knowledge.for"));
        scope.setItems(ProposalScope.SPACE, ProposalScope.GLOBAL);
        scope.setItemLabelGenerator(s -> getTranslation(s == ProposalScope.SPACE ? "knowledge.scope.space"
                : "knowledge.scope.everyone"));
        scope.setValue(ProposalScope.SPACE);
        TextField citation = new TextField(getTranslation("knowledge.source"));
        citation.setPlaceholder(getTranslation("knowledge.source.hint"));
        citation.setWidth("36rem");

        Runnable show = () -> {
            alias.setVisible(ALIAS.equals(kind.getValue()));
            pair.setVisible(PAIR.equals(kind.getValue()));
            pest.setVisible(HOST.equals(kind.getValue()));
        };
        kind.addValueChangeListener(e -> show.run());
        show.run();

        Div problems = new Div();
        Button propose = new Button(getTranslation("knowledge.propose"), e -> {
            problems.removeAll();
            Contribution contribution = switch (kind.getValue()) {
                case ALIAS -> new Contribution.Alias(id(target), name.getValue(), language.getValue());
                case PAIR -> new Contribution.Pair(id(crop), id(partner), relation.getValue(), effect.getValue());
                default -> new Contribution.Host(id(organism), id(host), month(from), month(to));
            };
            try {
                ProposalSummary added = enrichment.propose(spaceId, currentUser.login(),
                        scope.getValue(), contribution, citation.getValue());
                Notification.show(getTranslation(added.status() == ProposalStatus.APPROVED ? "knowledge.added"
                        : "knowledge.proposed"));
                name.clear();
                effect.clear();
                citation.clear();
                refresh();
            } catch (InvalidProposalException ex) {
                ex.problems().forEach(p -> problems.add(new Paragraph(p)));
            } catch (RuntimeException ex) {
                Notification.show(getTranslation("knowledge.failed", ex.getMessage()));
            }
        });
        propose.addThemeName("primary");
        return new VerticalLayout(kind, alias, pair, pest, row(scope, citation), propose, problems);
    }

    private void refresh() {
        mine.setItems(spaceId == 0 ? java.util.List.of() : enrichment.ofSpace(spaceId, currentUser.login()));
    }

    private void act(Runnable action) {
        try {
            action.run();
        } catch (InvalidProposalException ex) {
            Notification.show(String.join("; ", ex.problems()));
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
        refresh();
    }

    private String review(ProposalSummary p) {
        if (p.withdrawnBy() != null) {
            return getTranslation("knowledge.withdrawnby", p.withdrawnBy());
        }
        if (p.reviewedBy() == null) {
            return "";
        }
        return p.reviewedBy() + (p.reviewNote() == null ? "" : ": " + p.reviewNote());
    }

    private Locale pageLocale() {
        return getLocale() != null && getLocale().getLanguage().equals("en") ? Locale.ENGLISH : Locale.ITALIAN;
    }

    private static ComboBox<Option> combo(String label, List<Option> options) {
        ComboBox<Option> box = new ComboBox<>(label, options);
        box.setWidth("16rem");
        return box;
    }

    private ComboBox<Month> months(String label) {
        ComboBox<Month> box = new ComboBox<>(label, Month.values());
        box.setItemLabelGenerator(m -> m.getDisplayName(TextStyle.FULL, pageLocale()));
        return box;
    }

    private static HorizontalLayout row(com.vaadin.flow.component.Component... components) {
        HorizontalLayout row = new HorizontalLayout(components);
        row.setDefaultVerticalComponentAlignment(Alignment.END);
        row.setWrap(true);
        return row;
    }

    private static String id(ComboBox<Option> box) {
        return box.getValue() == null ? null : box.getValue().id();
    }

    /** 0 when not chosen, so the rules report it. */
    private static int month(ComboBox<Month> box) {
        return box.getValue() == null ? 0 : box.getValue().getValue();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("knowledge.title") + " · Rootology";
    }
}
