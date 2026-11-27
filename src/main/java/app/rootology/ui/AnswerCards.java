package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.VaadinIcon;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.enrichment.ProposalScope;
import app.rootology.facts.SourceCatalog;
import app.rootology.pipeline.PipelineAnswer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A finished answer as the same card everywhere (#31, #86): the sentence, the values, the confidence, the
 * notes, and the sources behind the values, each named in the page's language.
 */
final class AnswerCards {

    /**
     * @param askedAt    when the question was asked, from the Clock bean (#158), the month of the verdict
     * @param zone       the garden's own time zone (#158), for the verdict of a calendar answer
     * @param pageGarden the name of the garden the page is about, so the card does not say it again (#121);
     *                   null at the space level
     */
    static AnswerCard of(PipelineAnswer answer, Instant askedAt, java.time.ZoneId zone, long spaceId,
                         CurrentUser user, String pageGarden, SourceCatalog sources, KnowledgeEnrichment enrichment,
                         Component in) {
        return build(answer, askedAt, zone, spaceId, user, pageGarden, sources, enrichment, in, false);
    }

    /**
     * The same card for an answer rebuilt from the history (#111): an answer stored before sources were kept
     * says so (#114) rather than "no source", which read as an answer with nothing behind it. One stored since,
     * with no source, stays silent like a fresh one.
     *
     * @param askedAt         when the question was asked, for the verdict of a calendar answer (#116)
     * @param sourcesRecorded whether the row was stored with its sources (Flyway V33)
     */
    static AnswerCard ofPast(PipelineAnswer answer, Instant askedAt, java.time.ZoneId zone, boolean sourcesRecorded,
                             long spaceId, CurrentUser user, String pageGarden, SourceCatalog sources,
                             KnowledgeEnrichment enrichment, Component in) {
        return build(answer, askedAt, zone, spaceId, user, pageGarden, sources, enrichment, in, !sourcesRecorded);
    }

    private static AnswerCard build(PipelineAnswer answer, Instant askedAt, java.time.ZoneId zone, long spaceId,
                                   CurrentUser user, String pageGarden, SourceCatalog sources,
                                   KnowledgeEnrichment enrichment, Component in, boolean storedBeforeSources) {
        List<AnswerCard.SourceLine> lines = new ArrayList<>();
        for (String id : answer.sources()) {
            if (id.equals("garden")) {
                lines.add(new AnswerCard.SourceLine(VaadinIcon.HOME_O, in.getTranslation("ask.source.garden"), null));
            }
            enrichment.source(spaceId, user.login(), id).ifPresent(p -> lines.add(
                    new AnswerCard.SourceLine(VaadinIcon.USER_STAR, p.scope() == ProposalScope.GLOBAL
                            ? in.getTranslation("ask.source.global", String.valueOf(p.id()), p.citation())
                            : in.getTranslation("ask.source.space", user.nameOf(p.proposedBy()), String.valueOf(p.id())), null)));
            sources.byId(id).ifPresent(s -> lines.add(
                    new AnswerCard.SourceLine(VaadinIcon.BOOK, sourceTitle(id, s.title(), in), s.url())));
        }
        AnswerCard card = new AnswerCard();
        if (answer.context() != null) {
            card.context(answer.context(), pageGarden);
        }
        java.util.Optional<String> unknown = answer.notes().stream()
                .filter(n -> n.kind() == app.rootology.decision.Note.Kind.UNKNOWN_SUBJECT)
                .map(n -> n.arg(0)).findFirst();
        if (unknown.isPresent()) {
            // A name the base does not know (#180): no values, no sources; a member may say it is another name.
            card.unknown(unknown.get());
            if (user.canContribute(spaceId)) {
                card.extra(new com.vaadin.flow.component.html.Anchor("spaces/" + spaceId + "/knowledge?"
                        + KnowledgeView.ALIAS_NAME + "=" + java.net.URLEncoder.encode(unknown.get(),
                        java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"),
                        in.getTranslation("card.unknown.alias")));
            }
            return card;
        }
        CalendarVerdict.of(answer.answer(), answer.activities(), answer.context(), askedAt, zone, in)
                .ifPresent(card::verdict);
        if (answer.answeredByHuman()) {
            // The reviewer is stored by login; people read their display name (#106).
            card.answeredBy(answer.text(), answer.answer(), user.nameOf(answer.reviewer()), answer.activities());
        } else {
            card.answered(answer.text(), answer.answer(), answer.confidence(), answer.activities());
        }
        card.notes(answer.notes());
        if (lines.isEmpty() && storedBeforeSources) {
            return card.noSources();
        }
        return card.sources(lines);
    }

    /** A short title of a source in the page's language (#79), the catalogue's own title when there is none. */
    private static String sourceTitle(String id, String fallback, Component in) {
        String key = "source." + id;
        return Translations.has(key, NoteText.locale(in)) ? in.getTranslation(key) : fallback;
    }

    private AnswerCards() {
    }
}
