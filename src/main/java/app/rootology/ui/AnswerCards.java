package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.VaadinIcon;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.enrichment.ProposalScope;
import app.rootology.facts.SourceCatalog;
import app.rootology.pipeline.PipelineAnswer;

import java.util.ArrayList;
import java.util.List;

/**
 * A finished answer as the same card everywhere (#31, #86): the sentence, the values, the confidence, the
 * notes, and the sources behind the values, each named in the page's language.
 */
final class AnswerCards {

    static AnswerCard of(PipelineAnswer answer, long spaceId, String login, SourceCatalog sources,
                         KnowledgeEnrichment enrichment, Component in) {
        List<AnswerCard.SourceLine> lines = new ArrayList<>();
        for (String id : answer.sources()) {
            if (id.equals("garden")) {
                lines.add(new AnswerCard.SourceLine(VaadinIcon.HOME_O, in.getTranslation("ask.source.garden"), null));
            }
            enrichment.source(spaceId, login, id).ifPresent(p -> lines.add(
                    new AnswerCard.SourceLine(VaadinIcon.USER_STAR, p.scope() == ProposalScope.GLOBAL
                            ? in.getTranslation("ask.source.global", String.valueOf(p.id()), p.citation())
                            : in.getTranslation("ask.source.space", p.proposedBy(), String.valueOf(p.id())), null)));
            sources.byId(id).ifPresent(s -> lines.add(
                    new AnswerCard.SourceLine(VaadinIcon.BOOK, sourceTitle(id, s.title(), in), s.url())));
        }
        AnswerCard card = new AnswerCard();
        if (answer.context() != null) {
            card.context(answer.context());
        }
        return card.answered(answer.text(), answer.answer(), answer.confidence())
                .notes(answer.notes()).sources(lines);
    }

    /** A short title of a source in the page's language (#79), the catalogue's own title when there is none. */
    private static String sourceTitle(String id, String fallback, Component in) {
        String key = "source." + id;
        return Translations.has(key, NoteText.locale(in)) ? in.getTranslation(key) : fallback;
    }

    private AnswerCards() {
    }
}
