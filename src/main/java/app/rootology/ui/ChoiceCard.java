package app.rootology.ui;

import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import app.rootology.decision.Intent;
import app.rootology.facts.ClimateCells;
import app.rootology.garden.GardenService;
import app.rootology.geocoding.GeoNames;
import app.rootology.geocoding.Place;
import app.rootology.pipeline.ContextSnapshot;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.QuestionContext.Choice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The place or garden a question needs before it can be answered (#107): one short line naming what the question
 * looked for, and a chip per option under the field of Ask. A tap runs the same question again with that place,
 * or in that garden, through {@link Continuation}: no second understanding call and no daily limit. In a space
 * with no garden yet, creating one is the only way to answer a garden question; under an answer whose climate
 * came from a place, "Create a garden here" opens the garden dialog with the place already in it and the toast
 * after saving leads to the new garden. The state kept here is the waiting question with the intent Claude
 * already understood: the block goes when the question is continued or a new one is asked.
 */
class ChoiceCard extends Div {

    /** The question a choice was shown for, and the intent its run already understood (#107). */
    private record Waiting(String asked, Intent intent) {

        /** No question is waiting: nothing to continue. */
        static final Waiting NONE = new Waiting(null, null);
    }

    /** What a tap on a chip does (#107): the page runs the waiting question again with that place or garden. */
    @FunctionalInterface
    interface Continuation {
        /** Continues the question in the garden when gardenId is positive, otherwise with the place. */
        void continueWith(String asked, Intent intent, Choice.Place place, long gardenId);
    }

    private final GardenService gardens;
    private final GeoNames geoNames;
    private final ClimateCells cells;
    private final CurrentUser currentUser;
    /** Whether a question is running: a second tap starts nothing meanwhile (#82). */
    private final BooleanSupplier busy;
    /** Whether the person may ask at all (#33): a guest is offered no search and no garden to create. */
    private final BooleanSupplier mayAsk;
    private final Continuation continuation;

    /** The line above the chips, naming what the question was looking for. */
    private final Span line = new Span();
    /** The question the choice belongs to, so the person sees what the chips answer. */
    private final Span question = new Span();
    private final HorizontalLayout chips = new HorizontalLayout();
    /** "Another place…" (#107): the same GeoNames search the garden dialog uses, opened on demand. */
    private ComboBox<Place> anotherPlace;
    /** The waiting question, with the intent Claude already understood: what a tap runs again. */
    private Waiting waiting = Waiting.NONE;

    ChoiceCard(GardenService gardens, GeoNames geoNames, ClimateCells cells, CurrentUser currentUser,
               BooleanSupplier busy, BooleanSupplier mayAsk, Continuation continuation) {
        this.gardens = gardens;
        this.geoNames = geoNames;
        this.cells = cells;
        this.currentUser = currentUser;
        this.busy = busy;
        this.mayAsk = mayAsk;
        this.continuation = continuation;

        addClassName("rootology-ask-choice");
        line.addClassName("rootology-ask-choice-line");
        chips.addClassName("rootology-ask-chips");
        chips.addClassName("rootology-ask-choice-chips");
        setVisible(false);
        add(question, line, chips);
    }

    /**
     * The choice a run needs before it can be answered (#107), on the page of the space it belongs to: one line
     * and a chip per option, and a tap runs the same question again with that place or garden.
     */
    void show(GardenPipeline.Run run, String asked, long spaceId) {
        Choice needed = run.choice();
        waiting = new Waiting(asked, run.intent());
        line.setText(text(needed, run.intent()));
        question.setText(getTranslation("ask.choice.question", asked));
        question.addClassName("rootology-ask-choice-question");
        chips.removeAll();
        removeAll();
        add(question, line, chips);
        for (Choice.Place place : needed.places()) {
            chips.add(chip(place.label(), () -> choosePlace(place)));
        }
        for (Choice.Garden garden : needed.gardens()) {
            chips.add(chip(garden.name(), () -> chooseGarden(garden.id())));
        }
        if (needed.kind() == Choice.Kind.PLACE) {
            if (anotherPlaceAvailable()) {
                chips.add(chip(getTranslation("ask.choice.otherplace"), this::openAnotherPlace));
                // The search opens only from its chip: most people tap one of the places offered.
                add(anotherPlace());
                anotherPlace.setVisible(false);
            }
        } else if (needed.gardens().isEmpty()) {
            // The space has no garden yet: creating one is the only way to answer a garden question.
            chips.add(chip(getTranslation("ask.choice.creategarden"), () -> openNewGarden(spaceId)));
        }
        ChipGroup.roving(chips, question.getText());
        setVisible(true);
        setChipsEnabled(true);
    }

    /** The choice goes with the question that asked it: a new question, or a tap, takes it away (#107). */
    void hide() {
        waiting = Waiting.NONE;
        setVisible(false);
    }

    /** Whether the choice under the field is showing, for the tests (#107). */
    boolean showing() {
        return isVisible();
    }

    /** The chips of the choice, for the tests: a click is one choice (#107). */
    List<Button> chips() {
        return chips.getChildren().map(Button.class::cast).toList();
    }

    /** The line above the chips, for the tests: it names what the question was looking for (#107). */
    String line() {
        return line.getText();
    }

    /**
     * The link under a place's answer that opens a garden there (#107), with the place's names, position,
     * country and altitude already in the dialog. Nothing is offered for an answer that used no place, for a
     * place that already has a garden, or to whoever may not ask.
     */
    Optional<Button> gardenHere(GardenPipeline.Run run, long spaceId) {
        ContextSnapshot context = run.answer() == null ? null : run.answer().context();
        if (!mayAsk.getAsBoolean() || run.place() == null || context == null
                || context.climateFrom() != ContextSnapshot.ClimateFrom.PLACE || hasGardenAt(spaceId, run.place())) {
            return Optional.empty();
        }
        Button create = new Button(getTranslation("ask.createhere"), VaadinIcon.PLUS.create(),
                e -> createGardenAt(spaceId, run.place()));
        create.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        create.addClassName("rootology-answer-create");
        return Optional.of(create);
    }

    /** True when one of the space's gardens is already in that town or within about 5 km of the place. */
    private boolean hasGardenAt(long spaceId, Choice.Place place) {
        return gardens.gardens(spaceId, currentUser.login()).stream().anyMatch(g ->
                (g.town() != null && g.town().strip().equalsIgnoreCase(place.name()))
                        || (g.latitude() != null && g.longitude() != null && place.latitude() != null
                        && place.longitude() != null
                        && g.latitude().subtract(place.latitude()).abs().doubleValue() < 0.05
                        && g.longitude().subtract(place.longitude()).abs().doubleValue() < 0.05));
    }

    /** The line above the chips, naming what the question was looking for, in the page's language. */
    private String text(Choice needed, Intent intent) {
        String asked = intent == null || intent.place() == null ? "" : intent.place();
        return switch (needed.kind()) {
            case PLACE -> switch (needed.why()) {
                case MISSING -> getTranslation("ask.choice.place");
                case NOT_FOUND -> getTranslation("ask.choice.place.notfound", asked);
                case AMBIGUOUS -> getTranslation("ask.choice.place.ambiguous", asked);
            };
            case GARDEN -> getTranslation("ask.choice.garden");
        };
    }

    /** One option, as a chip: a button with the look of the suggested questions, reachable from the keyboard. */
    private Button chip(String text, Runnable action) {
        Button button = new Button(text, e -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        button.addClassName("rootology-ask-chip");
        return button;
    }

    /** The chips stay visible but do nothing while the continuation runs, so one tap is one question. */
    private void setChipsEnabled(boolean enabled) {
        chips.getChildren().filter(HasEnabled.class::isInstance).map(HasEnabled.class::cast)
                .forEach(c -> c.setEnabled(enabled));
    }

    /** A tap on a place chip: the same question again, with the place the person picked (#107). */
    private void choosePlace(Choice.Place place) {
        choose(place, 0);
    }

    /** A tap on a garden chip: the same question again, in the garden the person picked (#107). */
    private void chooseGarden(long gardenId) {
        choose(null, gardenId);
    }

    /** A tap takes the choice away and hands the waiting question to the page, which runs it again (#107). */
    private void choose(Choice.Place place, long gardenId) {
        if (busy.getAsBoolean() || waiting.asked() == null || waiting.intent() == null) {
            return;
        }
        Waiting pending = waiting;
        setChipsEnabled(false);
        hide();
        continuation.continueWith(pending.asked(), pending.intent(), place, gardenId);
    }

    /** Whether "another place" is offered: only when GeoNames is configured, and only to whoever asks (#30). */
    private boolean anotherPlaceAvailable() {
        return mayAsk.getAsBoolean() && geoNames != null && geoNames.enabled();
    }

    /** The search field of "another place" (#107), built once: the same search as the garden dialog's. */
    private ComboBox<Place> anotherPlace() {
        if (anotherPlace == null) {
            anotherPlace = PlaceSearch.field(geoNames, getTranslation("ask.choice.search"), "",
                    NoteText.locale(this).getLanguage());
            anotherPlace.addClassName("rootology-ask-choice-search");
            anotherPlace.addValueChangeListener(e -> {
                Place found = e.getValue();
                if (found != null) {
                    choosePlace(new Choice.Place(found.name(), found.region(), found.countryCode(),
                            found.countryName(), BigDecimal.valueOf(found.latitude()),
                            BigDecimal.valueOf(found.longitude()), null));
                }
            });
        }
        return anotherPlace;
    }

    private void openAnotherPlace() {
        ComboBox<Place> search = anotherPlace();
        search.setVisible(true);
        search.focus();
    }

    /** The gardens overview with its new-garden dialog open: no garden, no garden question can be answered. */
    private void openNewGarden(long spaceId) {
        getUI().ifPresent(ui -> ui.navigate(GardensView.class,
                new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId))),
                QueryParameters.simple(Map.of(GardensView.NEW, ""))));
    }

    /** The garden dialog on the place, and a toast that leads to the garden once it is saved (#107). */
    private void createGardenAt(long spaceId, Choice.Place place) {
        new GardenDialog(geoNames, cells, (name, town, latitude, longitude, countryCode, elevationM, timeZone) ->
                gardens.saveGarden(spaceId, currentUser.login(), null, name, town, latitude, longitude,
                        countryCode, elevationM, timeZone))
                .open(null, saved -> Toasts.opening(getTranslation("ask.createhere.done", saved.name()),
                        getTranslation("ask.createhere.open"), () -> openGarden(spaceId, saved.id())), place);
    }

    /** A garden's home: where the toast of a garden just created leads (#107). */
    private void openGarden(long spaceId, long gardenId) {
        getUI().ifPresent(ui -> ui.navigate(GardenHomeView.class, new RouteParameters(Map.of(
                Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId)))));
    }
}
