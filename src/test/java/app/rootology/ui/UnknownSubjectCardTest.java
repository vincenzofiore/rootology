package app.rootology.ui;

import app.rootology.decision.Note;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.SourceCatalog;
import app.rootology.pipeline.PipelineAnswer;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The answer about a crop the knowledge base does not know (#180): said plainly, no value, and for a member the
 * alias form of the Knowledge page with the name already in it.
 */
class UnknownSubjectCardTest {

    private static final PipelineAnswer UNKNOWN = new PipelineAnswer("ask", List.of(), 0.0, false, false, null,
            "The knowledge base does not know \"aglione\" yet", List.of(),
            List.of(Note.of(Note.Kind.UNKNOWN_SUBJECT, "aglione")), null);

    @Test
    void aMemberIsToldAndOfferedTheAliasForm() {
        AnswerCard card = card(true);

        assertThat(card.getElement().getTextRecursively()).contains("!{card.unknown}!", "!{card.unknown.text}!")
                .doesNotContain("does not know");
        assertThat(all(card).filter(Anchor.class::isInstance).map(a -> ((Anchor) a).getHref()))
                .containsExactly("spaces/4/knowledge?alias=aglione");
    }

    @Test
    void aGuestIsToldOnly() {
        assertThat(all(card(false))).noneMatch(Anchor.class::isInstance);
    }

    private static AnswerCard card(boolean member) {
        CurrentUser user = mock(CurrentUser.class);
        when(user.canContribute(4)).thenReturn(member);
        return AnswerCards.of(UNKNOWN, 4, user, null, mock(SourceCatalog.class), mock(KnowledgeEnrichment.class),
                new Div());
    }

    private static Stream<Component> all(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(UnknownSubjectCardTest::all));
    }
}
