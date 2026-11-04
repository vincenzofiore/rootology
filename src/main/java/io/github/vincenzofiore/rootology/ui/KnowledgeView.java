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
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.enrichment.InvalidProposalException;
import io.github.vincenzofiore.rootology.enrichment.KnowledgeEnrichment;
import io.github.vincenzofiore.rootology.enrichment.ProposalScope;
import io.github.vincenzofiore.rootology.enrichment.ProposalStatus;
import io.github.vincenzofiore.rootology.enrichment.ProposalSummary;
import io.github.vincenzofiore.rootology.facts.Contribution;
import io.github.vincenzofiore.rootology.roles.AppRole;
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
        add(new H3(getTranslation("knowledge.title")), new Paragraph(getTranslation("knowledge.intro")),
                askedAbout);
        // A guest only looks (#33): no proposals, no withdrawals.
        if (currentUser.canContribute()) {
            add(form());
        }

        mine.addColumn(this::describe).setHeader(getTranslation("knowledge.fact")).setFlexGrow(3);
        mine.addColumn(p -> getTranslation(p.scope() == ProposalScope.SPACE ? "knowledge.for.space" : "knowledge.for.everyone"))
                .setHeader(getTranslation("knowledge.for"))
                .setAutoWidth(true);
        mine.addColumn(p -> getTranslation("proposal." + p.status().name())).setHeader(getTranslation("knowledge.status"))
                .setAutoWidth(true);
        mine.addColumn(ProposalSummary::proposedBy).setHeader(getTranslation("knowledge.by")).setAutoWidth(true);
        mine.addColumn(this::review).setHeader(getTranslation("knowledge.review")).setFlexGrow(2);
        mine.addComponentColumn(p -> {
            Button withdraw = new Button(getTranslation("knowledge.withdraw"), e -> act(() ->
                    enrichment.withdraw(currentUser.space().id(), currentUser.login(), p.id())));
            withdraw.setVisible(currentUser.canContribute() && (p.status() == ProposalStatus.PENDING
                    || (p.status() == ProposalStatus.APPROVED && p.scope() == ProposalScope.SPACE)));
            return withdraw;
        }).setAutoWidth(true);
        mine.setAllRowsVisible(true);
        add(new H4(getTranslation("knowledge.mine")), mine);

        if (currentUser.hasRole(AppRole.CURATOR)) {
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
                ProposalSummary added = enrichment.propose(currentUser.space().id(), currentUser.login(),
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

    private VerticalLayout curatorSection() {
        TextField note = new TextField(getTranslation("knowledge.note"));
        note.setWidth("36rem");
        queue.addColumn(this::describe).setHeader(getTranslation("knowledge.fact")).setFlexGrow(3);
        queue.addColumn(ProposalSummary::citation).setHeader(getTranslation("knowledge.source")).setFlexGrow(2);
        queue.addColumn(ProposalSummary::proposedBy).setHeader(getTranslation("knowledge.by")).setAutoWidth(true);
        queue.addComponentColumn(p -> new HorizontalLayout(
                new Button(getTranslation("knowledge.approve"), e -> act(() ->
                        enrichment.approve(currentUser.login(), p.id(), note.getValue()))),
                new Button(getTranslation("knowledge.reject"), e -> act(() ->
                        enrichment.reject(currentUser.login(), p.id(), note.getValue())))))
                .setAutoWidth(true);
        queue.setAllRowsVisible(true);
        return new VerticalLayout(new H4(getTranslation("knowledge.curator")),
                new Paragraph(getTranslation("knowledge.curator.intro")), note, queue);
    }

    private void refresh() {
        mine.setItems(enrichment.ofSpace(currentUser.space().id(), currentUser.login()));
        if (currentUser.hasRole(AppRole.CURATOR)) {
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
            case Contribution.Alias a -> getTranslation("knowledge.describe.alias", a.name(),
                    getTranslation("knowledge.language." + a.language()), label(a.targetId()));
            case Contribution.Pair pair -> getTranslation("COMPANION".equals(pair.relation())
                    ? "knowledge.describe.companion" : "knowledge.describe.antagonist", label(pair.cropId()),
                    label(pair.partnerId()), pair.effect());
            case Contribution.Host h -> getTranslation("knowledge.describe.host", label(h.organismId()), label(h.cropId()),
                    monthName(h.fromMonth()), monthName(h.toMonth()));
        };
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

    private String label(String id) {
        return labels.getOrDefault(id, id);
    }

    private String monthName(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, pageLocale());
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
