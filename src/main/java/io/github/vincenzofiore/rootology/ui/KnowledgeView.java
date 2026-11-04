package io.github.vincenzofiore.rootology.ui;

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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.enrichment.InvalidProposalException;
import io.github.vincenzofiore.rootology.enrichment.KnowledgeEnrichment;
import io.github.vincenzofiore.rootology.enrichment.ProposalScope;
import io.github.vincenzofiore.rootology.enrichment.ProposalStatus;
import io.github.vincenzofiore.rootology.enrichment.ProposalSummary;
import io.github.vincenzofiore.rootology.facts.Contribution;
import io.github.vincenzofiore.rootology.facts.CropCatalog;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph;
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
 */
@Route(value = "knowledge", layout = MainLayout.class)
@PageTitle("Knowledge · Rootology")
@PermitAll
public class KnowledgeView extends VerticalLayout implements BeforeEnterObserver {

    /** A crop or an organism as offered in the forms. */
    record Option(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    static final String ALIAS = "Another name";
    static final String PAIR = "A crop pair";
    static final String HOST = "A pest or disease on a crop";

    private final KnowledgeEnrichment enrichment;
    private final CurrentUser currentUser;
    private final List<Option> crops;
    private final List<Option> organisms;
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final Paragraph askedAbout = new Paragraph();
    private final Grid<ProposalSummary> mine = new Grid<>();
    private final Grid<ProposalSummary> queue = new Grid<>();

    public KnowledgeView(KnowledgeEnrichment enrichment, CropCatalog cropCatalog, KnowledgeGraph knowledge,
                         CurrentUser currentUser) {
        this.enrichment = enrichment;
        this.currentUser = currentUser;
        this.crops = cropCatalog.all().stream()
                .map(c -> new Option(c.id(), c.italianName() + " (" + c.englishName() + ")")).toList();
        this.organisms = knowledge.organisms().stream()
                .map(o -> new Option(o.id(), o.italianName() + " (" + o.englishName() + ")")).toList();
        crops.forEach(o -> labels.put(o.id(), o.label()));
        organisms.forEach(o -> labels.put(o.id(), o.label()));

        askedAbout.setVisible(false);
        add(new H3("Knowledge"),
                new Paragraph("Is a name, a crop pair or a pest missing? Add it for this space, where it applies "
                        + "at once and is marked as proposed in the answers, or propose it for everyone with a "
                        + "source: a curator reviews it first."),
                askedAbout, form());

        mine.addColumn(this::describe).setHeader("Fact").setFlexGrow(3);
        mine.addColumn(p -> p.scope() == ProposalScope.SPACE ? "This space" : "Everyone").setHeader("For")
                .setAutoWidth(true);
        mine.addColumn(p -> p.status().name()).setHeader("Status").setAutoWidth(true);
        mine.addColumn(ProposalSummary::proposedBy).setHeader("By").setAutoWidth(true);
        mine.addColumn(this::review).setHeader("Review").setFlexGrow(2);
        mine.addComponentColumn(p -> {
            Button withdraw = new Button("Withdraw", e -> act(() ->
                    enrichment.withdraw(currentUser.space().id(), currentUser.login(), p.id())));
            withdraw.setVisible(p.status() == ProposalStatus.PENDING
                    || (p.status() == ProposalStatus.APPROVED && p.scope() == ProposalScope.SPACE));
            return withdraw;
        }).setAutoWidth(true);
        mine.setAllRowsVisible(true);
        add(new H4("Proposed in this space"), mine);

        if (enrichment.isCurator(currentUser.login())) {
            add(curatorSection());
        }
        refresh();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // From Ask: the question that could not be answered, as a reminder of what is missing.
        List<String> question = event.getLocation().getQueryParameters().getParameters().getOrDefault("question",
                List.of());
        askedAbout.setVisible(!question.isEmpty());
        if (!question.isEmpty()) {
            askedAbout.setText("You asked: \"" + question.getFirst() + "\"");
        }
    }

    private VerticalLayout form() {
        RadioButtonGroup<String> kind = new RadioButtonGroup<>("What is missing", ALIAS, PAIR, HOST);
        kind.setValue(ALIAS);

        List<Option> named = new ArrayList<>(crops);
        named.addAll(organisms);
        ComboBox<Option> target = combo("Crop, pest or disease", named);
        TextField name = new TextField("Other name");
        RadioButtonGroup<String> language = new RadioButtonGroup<>("Language", "it", "en");
        language.setValue("it");
        HorizontalLayout alias = row(target, name, language);

        ComboBox<Option> crop = combo("Crop", crops);
        ComboBox<Option> partner = combo("With", crops);
        RadioButtonGroup<String> relation = new RadioButtonGroup<>("Relation", "COMPANION", "ANTAGONIST");
        relation.setValue("COMPANION");
        TextField effect = new TextField("Effect");
        effect.setPlaceholder("What happens when they grow together");
        HorizontalLayout pair = row(crop, partner, relation, effect);

        ComboBox<Option> organism = combo("Pest or disease", organisms);
        ComboBox<Option> host = combo("On crop", crops);
        ComboBox<Month> from = months("From");
        ComboBox<Month> to = months("To");
        HorizontalLayout pest = row(organism, host, from, to);

        RadioButtonGroup<ProposalScope> scope = new RadioButtonGroup<>("For");
        scope.setItems(ProposalScope.SPACE, ProposalScope.GLOBAL);
        scope.setItemLabelGenerator(s -> s == ProposalScope.SPACE ? "Only this space (applies now)"
                : "Everyone (a curator reviews it)");
        scope.setValue(ProposalScope.SPACE);
        TextField citation = new TextField("Source");
        citation.setPlaceholder("A link or a reference; required for everyone");
        citation.setWidth("36rem");

        Runnable show = () -> {
            alias.setVisible(ALIAS.equals(kind.getValue()));
            pair.setVisible(PAIR.equals(kind.getValue()));
            pest.setVisible(HOST.equals(kind.getValue()));
        };
        kind.addValueChangeListener(e -> show.run());
        show.run();

        Div problems = new Div();
        Button propose = new Button("Propose", e -> {
            problems.removeAll();
            Contribution contribution = switch (kind.getValue()) {
                case ALIAS -> new Contribution.Alias(id(target), name.getValue(), language.getValue());
                case PAIR -> new Contribution.Pair(id(crop), id(partner), relation.getValue(), effect.getValue());
                default -> new Contribution.Host(id(organism), id(host), month(from), month(to));
            };
            try {
                ProposalSummary added = enrichment.propose(currentUser.space().id(), currentUser.login(),
                        scope.getValue(), contribution, citation.getValue());
                Notification.show(added.status() == ProposalStatus.APPROVED ? "Added for this space"
                        : "Proposed; a curator will review it");
                name.clear();
                effect.clear();
                citation.clear();
                refresh();
            } catch (InvalidProposalException ex) {
                ex.problems().forEach(p -> problems.add(new Paragraph(p)));
            } catch (RuntimeException ex) {
                Notification.show("Could not propose: " + ex.getMessage());
            }
        });
        propose.addThemeName("primary");
        return new VerticalLayout(kind, alias, pair, pest, row(scope, citation), propose, problems);
    }

    private VerticalLayout curatorSection() {
        TextField note = new TextField("Review note");
        note.setWidth("36rem");
        queue.addColumn(this::describe).setHeader("Fact").setFlexGrow(3);
        queue.addColumn(ProposalSummary::citation).setHeader("Source").setFlexGrow(2);
        queue.addColumn(ProposalSummary::proposedBy).setHeader("By").setAutoWidth(true);
        queue.addComponentColumn(p -> new HorizontalLayout(
                new Button("Approve", e -> act(() -> enrichment.approve(currentUser.login(), p.id(), note.getValue()))),
                new Button("Reject", e -> act(() -> enrichment.reject(currentUser.login(), p.id(), note.getValue())))))
                .setAutoWidth(true);
        queue.setAllRowsVisible(true);
        return new VerticalLayout(new H4("Waiting for a curator"),
                new Paragraph("Facts proposed for everyone. Check the source before approving; an approved fact "
                        + "reaches every space as curated."), note, queue);
    }

    private void refresh() {
        mine.setItems(enrichment.ofSpace(currentUser.space().id(), currentUser.login()));
        if (enrichment.isCurator(currentUser.login())) {
            queue.setItems(enrichment.pending(currentUser.login()));
        }
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

    String describe(ProposalSummary p) {
        return switch (p.contribution()) {
            case Contribution.Alias a -> "\"" + a.name() + "\" (" + a.language() + ") is a name of " + label(a.targetId());
            case Contribution.Pair pair -> label(pair.cropId()) + " is "
                    + ("COMPANION".equals(pair.relation()) ? "a companion" : "an antagonist") + " of "
                    + label(pair.partnerId()) + ": " + pair.effect();
            case Contribution.Host h -> label(h.organismId()) + " affects " + label(h.cropId()) + ", "
                    + monthName(h.fromMonth()) + " to " + monthName(h.toMonth());
        };
    }

    private String review(ProposalSummary p) {
        if (p.withdrawnBy() != null) {
            return "withdrawn by " + p.withdrawnBy();
        }
        if (p.reviewedBy() == null) {
            return "";
        }
        return p.reviewedBy() + (p.reviewNote() == null ? "" : ": " + p.reviewNote());
    }

    private String label(String id) {
        return labels.getOrDefault(id, id);
    }

    private static String monthName(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static ComboBox<Option> combo(String label, List<Option> options) {
        ComboBox<Option> box = new ComboBox<>(label, options);
        box.setWidth("16rem");
        return box;
    }

    private static ComboBox<Month> months(String label) {
        ComboBox<Month> box = new ComboBox<>(label, Month.values());
        box.setItemLabelGenerator(m -> m.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
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
}
