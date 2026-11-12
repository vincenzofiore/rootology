package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import app.rootology.pipeline.GardenPipeline.PendingReview;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.resilience.ServiceUnavailableException;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * One waiting question as a card (#86): the question, the context it was asked in, why it waits, the notes,
 * who asked and when, and, for a person who may answer, the values proposed by the decision layer as chips
 * with a field to add values from the catalogue, an explicit "none", a note and one answer button. The same
 * card is shown on Escalations and while Ask waits, so answering looks the same in both places (#33, #55).
 */
class WaitingQuestionCard extends AnswerCard {

    /** Hands the draft to the pipeline: the view supplies its page's space, garden and person. */
    @FunctionalInterface
    interface Answerer {
        PipelineAnswer respond(List<String> values, String note);
    }

    private final AnswerDraft draft;
    private final Answerer answerer;
    private final Function<PipelineAnswer, Component> finished;
    private final Div chips = new Div();
    private final ComboBox<String> add = new ComboBox<>();
    private final Checkbox none;
    private final TextField note = new TextField();
    private final Button answer = new Button();

    WaitingQuestionCard(PendingReview review, boolean mayAnswer, List<String> suggestions,
                        Answerer answerer, Function<PipelineAnswer, Component> finished) {
        this.draft = new AnswerDraft(review.decision().answer());
        this.answerer = answerer;
        this.finished = finished;
        addClassName("rootology-waiting-card");

        H4 question = new H4(review.question().text());
        question.addClassName("rootology-waiting-question");
        add(question);
        if (review.snapshot() != null) {
            context(ContextText.line(review.snapshot(), this));
        }
        waiting(ReasonText.text(review.decision().reason(), this));
        notes(review.notes());
        add(meta(review));
        chips.addClassName("rootology-chip-row");
        add(chips);
        if (mayAnswer) {
            none = new Checkbox(getTranslation("escalations.none"));
            addControls(suggestions);
        } else {
            none = null;
        }
        renderChips();
    }

    /** Who asked and when, and how sure the decision layer was: the number, clearly labelled. */
    private Div meta(PendingReview review) {
        Div meta = new Div();
        meta.addClassName("rootology-waiting-meta");
        if (review.context().startedBy() != null) {
            meta.add(new Span(getTranslation("escalations.asked", review.context().startedBy())));
        }
        if (review.askedAt() != null) {
            meta.add(DateText.dateTimeSpan(review.askedAt(), this));
        }
        return meta;
    }

    private void addControls(List<String> suggestions) {
        add.setLabel(getTranslation("escalations.values"));
        add.setPlaceholder(getTranslation("escalations.add"));
        add.setItems(suggestions);
        add.setAllowCustomValue(true);
        add.setWidthFull();
        add.addValueChangeListener(e -> {
            if (e.getValue() != null && !e.getValue().isBlank()) {
                draft.add(e.getValue());
                add.clear();
                renderChips();
            }
        });
        Button addButton = RowActions.add(getTranslation("escalations.addvalue"), e -> addTyped());
        HorizontalLayout field = new HorizontalLayout(add, addButton);
        field.setWidthFull();
        field.setDefaultVerticalComponentAlignment(Alignment.END);
        field.setWrap(true);
        field.addClassName("rootology-answer-field");

        note.setLabel(getTranslation("escalations.note"));
        note.setWidthFull();
        none.addValueChangeListener(e -> {
            draft.none(e.getValue());
            renderChips();
            answer.setEnabled(true);
        });

        answer.setText(getTranslation("escalations.answer"));
        answer.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        answer.addClickListener(e -> submit());
        HorizontalLayout actions = new HorizontalLayout(none, answer);
        actions.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        actions.setWrap(true);
        actions.addClassName("rootology-answer-actions");

        Div controls = new Div(field, note, actions);
        controls.addClassName("rootology-answer-controls");
        add(controls);
    }

    private void addTyped() {
        draft.add(add.getValue());
        add.clear();
        renderChips();
    }

    /** The chips are the answer so far: the proposed values the person kept, plus the typed ones. */
    private void renderChips() {
        chips.removeAll();
        boolean off = none != null && none.getValue();
        List<String> values = draft.values();
        // A guest reads the values proposed but has nothing to answer with (#33).
        chips.setVisible(!off && (none != null || !values.isEmpty()));
        if (none != null) {
            add.setEnabled(!off);
        }
        if (off) {
            return;
        }
        if (values.isEmpty()) {
            Span empty = new Span(getTranslation("escalations.novalues"));
            empty.addClassName("rootology-waiting-novalues");
            chips.add(empty);
            return;
        }
        boolean removable = none != null;
        values.forEach(value -> chips.add(chip(value, removable)));
    }

    private Div chip(String value, boolean removable) {
        Div chip = new Div(new Span(value));
        if (removable) {
            Button remove = RowActions.remove(getTranslation("escalations.remove", value), e -> {
                draft.remove(value);
                renderChips();
            });
            chip.add(remove);
        }
        chip.addClassName("rootology-chip");
        return chip;
    }

    private void submit() {
        if (none != null && none.getValue()) {
            draft.none(true);
        } else if (add.getValue() != null && !add.getValue().isBlank()) {
            draft.add(add.getValue());
        }
        answer.setEnabled(false);
        try {
            PipelineAnswer result = answerer.respond(draft.answer(), note.getValue());
            show(finished.apply(result));
        } catch (ServiceUnavailableException ex) {
            // The answer is not stored when the agent cannot finish: say so instead of pretending (#86).
            answer.setEnabled(true);
            Notification.show(getTranslation("escalations.phrasenotavailable"), 8000,
                    Notification.Position.BOTTOM_START);
        } catch (RuntimeException ex) {
            answer.setEnabled(true);
            UiErrors.show(this, ex);
        }
    }

    /** The answer takes the card's place, so the person sees what their answer produced (#86). */
    private void show(Component replacement) {
        Element parent = getElement().getParent();
        if (parent != null) {
            int index = Math.max(0, parent.indexOfChild(getElement()));
            parent.insertChild(index, replacement.getElement());
            getElement().removeFromParent();
        } else {
            removeAll();
            add(replacement);
        }
    }

    /** Crops and organisms of the knowledge base, each under the name of the page's language (#86). */
    static List<String> suggestions(Stream<String> crops, Stream<String> organisms) {
        return Stream.concat(crops, organisms)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** Whether the answer controls exist, for the tests (#33). */
    boolean mayAnswer() {
        return none != null;
    }
}
