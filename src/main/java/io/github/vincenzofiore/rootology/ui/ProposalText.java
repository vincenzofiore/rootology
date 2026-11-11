package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.Component;
import io.github.vincenzofiore.rootology.enrichment.ProposalSummary;
import io.github.vincenzofiore.rootology.facts.Contribution;
import io.github.vincenzofiore.rootology.facts.CropCatalog;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How a proposed fact is worded in the lists (#24): the same words in the knowledge page and in the
 * curator queue, which live on two pages since #56.
 */
final class ProposalText {

    private final Map<String, String> labels = new LinkedHashMap<>();

    ProposalText(CropCatalog crops, KnowledgeGraph knowledge) {
        crops.all().forEach(c -> labels.put(c.id(), c.italianName() + " (" + c.englishName() + ")"));
        knowledge.organisms().forEach(o -> labels.put(o.id(), o.italianName() + " (" + o.englishName() + ")"));
    }

    String describe(Component page, ProposalSummary p) {
        return switch (p.contribution()) {
            case Contribution.Alias a -> page.getTranslation("knowledge.describe.alias", a.name(),
                    page.getTranslation("knowledge.language." + a.language()), label(a.targetId()));
            case Contribution.Pair pair -> page.getTranslation("COMPANION".equals(pair.relation())
                    ? "knowledge.describe.companion" : "knowledge.describe.antagonist", label(pair.cropId()),
                    label(pair.partnerId()), pair.effect());
            case Contribution.Host h -> page.getTranslation("knowledge.describe.host", label(h.organismId()),
                    label(h.cropId()), monthName(page, h.fromMonth()), monthName(page, h.toMonth()));
        };
    }

    private String label(String id) {
        return labels.getOrDefault(id, id);
    }

    private static String monthName(Component page, int month) {
        java.util.Locale locale = com.vaadin.flow.component.UI.getCurrent() == null ? null
                : com.vaadin.flow.component.UI.getCurrent().getLocale();
        boolean english = locale != null && locale.getLanguage().equals("en");
        return Month.of(month).getDisplayName(TextStyle.FULL, english ? Locale.ENGLISH : Locale.ITALIAN);
    }
}
