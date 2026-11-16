package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.Crop;
import app.rootology.facts.CropCatalog;
import app.rootology.garden.DirectSun;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenPlan;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlanArea;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.garden.Orientation;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.garden.Wind;
import app.rootology.geocoding.GeoNames;
import app.rootology.weather.DailyForecast;
import app.rootology.weather.WeatherService;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One garden of the space: its areas as cards, and what grows in each (#56, #85). The area is the growing
 * unit the person sees; the code and the database keep calling it a plot.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/areas", layout = MainLayout.class)
@PermitAll
public class AreasView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private final GardenService gardens;
    private final CropCatalog crops;
    private final GeoNames geoNames;
    private final ClimateCells climateCells;
    private final WeatherService weather;
    /** At most a frost marker here (#84): the weather card lives on the garden home. */
    private final Div frostMark = new Div();
    private final CurrentUser currentUser;
    private final String login;
    /** The page's own context (#56), read from its route. */
    private long spaceId;
    private GardenSummary garden;

    private final Paragraph gardenInfo = new Paragraph();
    private final Div areaCards = new Div();
    private final Button editGarden = new Button(getTranslation("garden.edit"), VaadinIcon.EDIT.create());
    private final Button addArea = new Button(getTranslation("garden.addplot"));
    private final HorizontalLayout top = new HorizontalLayout();
    private boolean contributor;
    /** What the page shows instead of empty cards (#81); the action is hidden for a guest (#33). */
    private final EmptyState noAreas = new EmptyState(getTranslation("empty.plots"),
            getTranslation("empty.plots.add"), () -> areaDialog(null));

    /** The garden's plan (#90), above the areas: the invitation when it has none, the plan and its size once drawn. */
    private final Div planSection = new Div();
    private final Div planInvite = new Div();
    private final Div planHead = new Div();
    private final Span planSize = new Span();
    /** Changes the plan's size; only a contributor sees it, and the dialog keeps the same fields as the invitation. */
    private final Button changePlanSize = RowActions.edit(getTranslation("plan.sizes.change"),
            e -> planSizeDialog());
    /** The areas drawn on the plan, moved and resized on the page (#90). */
    private final GardenPlanBoard board = new GardenPlanBoard();
    /** The areas of the garden that are not on the plan yet, offered to place. */
    private final Div unplaced = new Div();
    private final BigDecimalField planWidth = planField(getTranslation("plan.width"));
    private final BigDecimalField planDepth = planField(getTranslation("plan.depth"));
    /** The plan as last read, null when the page shows no garden; the cards and the board read it. */
    private GardenPlan plan;
    /** The areas of the garden, in the cards' order (#85); the plan's chips read their kind and surface. */
    private List<PlotSummary> areas = List.of();
    /** The area whose card or box is marked, 0 for none (#90). */
    private long selectedArea;

    public AreasView(GardenService gardens, CropCatalog crops, GeoNames geoNames, WeatherService weather,
                     ClimateCells climateCells,
                     CurrentUser currentUser) {
        this.weather = weather;
        this.gardens = gardens;
        this.crops = crops;
        this.geoNames = geoNames;
        this.climateCells = climateCells;
        this.currentUser = currentUser;
        this.login = currentUser.login();

        editGarden.addClickListener(e -> gardenDialog());
        // A guest only looks (#33): every action is hidden, and the role is known in beforeEnter.
        top.add(editGarden, addArea);
        top.setDefaultVerticalComponentAlignment(Alignment.END);
        addArea.addClickListener(e -> areaDialog(null));
        addArea.setIcon(VaadinIcon.PLUS.create());
        addArea.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        editGarden.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        showContributorActions(false);

        areaCards.addClassName("rootology-area-cards");
        noAreas.setVisible(false);
        frostMark.addClassName("rootology-areas-frost");
        frostMark.setVisible(false);
        buildPlan();
        add(new PageHeader(getTranslation("garden.plots"), top), gardenInfo, frostMark, planSection, areaCards,
                noAreas);
    }

    /**
     * The plan section (#90) is built once and only filled in as the garden is read: the invitation with its two
     * fields, the plan's line and its action, the board the areas are drawn on, and the areas still to place.
     */
    private void buildPlan() {
        planSection.addClassName("rootology-plan-section");
        planSection.setVisible(false);

        Paragraph hint = new Paragraph(getTranslation("plan.hint"));
        hint.addClassName("rootology-plan-hint");
        Button create = new Button(getTranslation("plan.create"),
                e -> savePlanSize(planWidth.getValue(), planDepth.getValue()));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout fields = new HorizontalLayout(planWidth, planDepth, create);
        fields.addClassName("rootology-plan-fields");
        fields.setDefaultVerticalComponentAlignment(Alignment.END);
        fields.setWrap(true);
        planInvite.addClassName("rootology-plan-invite");
        planInvite.add(hint, fields);

        planHead.addClassName("rootology-plan-head");
        planSize.addClassName("rootology-plan-size");
        planHead.add(new H4(getTranslation("plan.title")), planSize, changePlanSize);

        unplaced.addClassName("rootology-plan-unplaced");
        unplaced.setVisible(false);

        // A box on the plan and its card mark each other; a move or a resize is stored and read back (#90).
        board.addAreaSelectedListener(e -> board.selected(e.areaId()).ifPresent(this::selectFromPlan));
        board.addAreaMovedListener(e -> board.move(e.areaId(), e.x(), e.y(), e.width(), e.depth())
                .ifPresent(this::saveMove));

        planSection.add(planInvite, planHead, board, unplaced);
    }

    /** The page works in its route's garden; a space or garden the person cannot see is not found. */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        showContributorActions(currentUser.canContribute(spaceId));
        showGarden(gardens.garden(spaceId, login, where.get().gardenId()).orElse(null));
    }

    /** Only an OWNER or MEMBER changes the garden; a guest only looks (#33). */
    private void showContributorActions(boolean contributor) {
        editGarden.setVisible(contributor);
        addArea.setVisible(contributor);
        noAreas.actionVisible(contributor);
        this.contributor = contributor;
    }

    private void showGarden(GardenSummary garden) {
        this.garden = garden;
        if (garden == null) {
            frostMark.removeAll();
            frostMark.setVisible(false);
            gardenInfo.setText(getTranslation("garden.none"));
            areaCards.removeAll();
            areaCards.setVisible(false);
            noAreas.setVisible(false);
            planSection.setVisible(false);
            addArea.setVisible(false);
            return;
        }
        // Town and country are enough (#79): the exact position is the map's business.
        String where = garden.town() == null ? getTranslation("garden.notown") : garden.town();
        String inCountry = garden.countryCode() == null ? "" : ", " + garden.countryCode();
        gardenInfo.setText(where + inCountry);
        showFrost(garden);
        refreshAreas();
    }

    /** Reads the areas and the plan again (#90): what the cards and the board show both come from them. */
    private void refreshAreas() {
        // The plan first: the cards read it for their take-off action and their surface.
        plan = garden == null ? null : gardens.plan(spaceId, login, garden.id());
        showAreas();
        showPlan();
    }

    /**
     * The plan section (#90): the invitation with two fields when the garden has none, the plan with its size
     * once drawn, the areas still to place under it. A guest only sees a plan that exists, never the invitation.
     */
    private void showPlan() {
        boolean drawn = plan != null && plan.drawn();
        planInvite.setVisible(contributor && plan != null && !drawn);
        planHead.setVisible(drawn);
        board.setVisible(drawn);
        if (!drawn) {
            unplaced.setVisible(false);
            planSection.setVisible(drawn || (plan != null && contributor));
            return;
        }
        planSize.setText(getTranslation("plan.dimensions", metres(plan.width()), metres(plan.depth())));
        changePlanSize.setVisible(contributor);
        board.setContributor(contributor);
        board.setPlanLabel(getTranslation("plan.title"));
        board.setPlan(plan.width().doubleValue(), plan.depth().doubleValue());
        board.setBoxes(boxes(plan));
        showUnplaced(plan);
        planSection.setVisible(true);
    }

    /** The areas on the plan as the element draws them: their name, the drawing and the word of their kind. */
    private List<GardenPlanBoard.Box> boxes(GardenPlan plan) {
        return plan.areas().stream().filter(PlanArea::placed)
                .map(area -> new GardenPlanBoard.Box(Long.toString(area.plotId()), area.name(),
                        AreaKindIcons.of(area.kind()),
                        getTranslation("plan.dimensions", metres(area.width()), metres(area.depth())),
                        getTranslation("plan.box.label", area.name(), AreaKindIcons.word(area.kind(), this),
                                metres(area.width()), metres(area.depth()), metres(area.x()), metres(area.y())),
                        area.x().doubleValue(), area.y().doubleValue(), area.width().doubleValue(),
                        area.depth().doubleValue()))
                .toList();
    }

    /** The areas not on the plan yet, as chips that place one at the first free spot (#90). */
    private void showUnplaced(GardenPlan plan) {
        unplaced.removeAll();
        List<PlanArea> free = plan.areas().stream().filter(area -> !area.placed()).toList();
        if (!contributor || free.isEmpty()) {
            unplaced.setVisible(false);
            return;
        }
        Span label = new Span(getTranslation("plan.unplaced"));
        label.addClassName("rootology-plan-unplaced-label");
        unplaced.add(label);
        for (PlanArea area : free) {
            Button chip = new Button(area.name(), new SvgIcon(AreaKindIcons.of(area.kind())),
                    e -> placeOnPlan(area));
            chip.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            chip.addClassName("rootology-plan-chip");
            chip.getElement().setAttribute("data-area", Long.toString(area.plotId()));
            String word = getTranslation("plan.place", area.name());
            chip.setAriaLabel(word);
            chip.setTooltipText(word);
            unplaced.add(chip);
        }
        unplaced.setVisible(true);
    }

    /** Puts an area on the plan where it first fits, at the size its surface or its kind gives it (#90). */
    private void placeOnPlan(PlanArea area) {
        if (plan == null || !plan.drawn()) {
            return;
        }
        PlotSummary summary = areas.stream().filter(shown -> shown.id() == area.plotId()).findFirst().orElse(null);
        PlotKind kind = summary == null ? area.kind() : summary.kind();
        BigDecimal declared = summary == null ? null : summary.areaM2();
        double width = plan.width().doubleValue();
        double depth = plan.depth().doubleValue();
        PlanPlacement.Box size = PlanPlacement.defaultSize(kind, declared, width, depth);
        List<PlanPlacement.Box> taken = plan.areas().stream().filter(PlanArea::placed)
                .map(placed -> new PlanPlacement.Box(placed.x().doubleValue(), placed.y().doubleValue(),
                        placed.width().doubleValue(), placed.depth().doubleValue()))
                .toList();
        PlanPlacement.Box spot = PlanPlacement.firstFree(size, taken, width, depth);
        try {
            gardens.placeArea(spaceId, login, area.plotId(), BigDecimal.valueOf(spot.x()),
                    BigDecimal.valueOf(spot.y()), BigDecimal.valueOf(spot.width()),
                    BigDecimal.valueOf(spot.depth()));
            refreshAreas();
            selectFromPlan(area.plotId());
        } catch (RuntimeException ex) {
            UiErrors.show(this, ex);
        }
    }

    /** A box was dragged, resized or moved with the keys: the service checks it, then the page reads it back. */
    private void saveMove(GardenPlanBoard.Move move) {
        try {
            gardens.placeArea(spaceId, login, move.areaId(), move.x(), move.y(), move.width(), move.depth());
        } catch (RuntimeException ex) {
            UiErrors.show(this, ex);
        }
        refreshAreas();
    }

    /** A box on the plan was tapped: its card is marked and scrolled to (#90). */
    private void selectFromPlan(long areaId) {
        markCard(areaId);
        areaCards.getChildren().forEach(card -> {
            if (Long.toString(areaId).equals(card.getElement().getAttribute("data-area"))) {
                card.scrollIntoView();
            }
        });
    }

    /** A card was tapped: its box on the plan is marked, and readable with the keys (#90). */
    private void selectFromCard(long areaId) {
        if (plan != null && plan.drawn()) {
            board.select(areaId);
        }
        markCard(areaId);
    }

    private void markCard(long areaId) {
        selectedArea = areaId;
        areaCards.getChildren().forEach(card -> card.getElement().getClassList()
                .set("rootology-area-card-selected",
                        Long.toString(areaId).equals(card.getElement().getAttribute("data-area"))));
    }

    /** The plan's size, or a first one: the same two fields as the invitation, in a small dialog (#90). */
    private void planSizeDialog() {
        BigDecimalField width = planField(getTranslation("plan.width"));
        BigDecimalField depth = planField(getTranslation("plan.depth"));
        if (plan != null) {
            width.setValue(plan.width());
            depth.setValue(plan.depth());
        }
        Dialog dialog = new Dialog(getTranslation("plan.sizes"));
        dialog.add(new FormLayout(width, depth));
        dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()),
                new Button(getTranslation("common.save"), e -> {
                    if (savePlanSize(width.getValue(), depth.getValue())) {
                        dialog.close();
                    }
                }));
        dialog.open();
    }

    /**
     * Draws the plan or gives it a new size (#90). A size the service refuses, because an area would be left
     * outside, is said in the page's language: the person moves the area first.
     */
    private boolean savePlanSize(BigDecimal width, BigDecimal depth) {
        BigDecimal min = GardenService.MIN_AREA_SIDE;
        BigDecimal max = GardenService.MAX_PLAN_SIDE;
        if (width == null || depth == null || width.compareTo(min) < 0 || depth.compareTo(min) < 0
                || width.compareTo(max) > 0 || depth.compareTo(max) > 0) {
            Toasts.problem(getTranslation("plan.sizes.range"));
            return false;
        }
        try {
            gardens.resizePlan(spaceId, login, garden.id(), width, depth);
        } catch (IllegalArgumentException refused) {
            Toasts.problem(getTranslation("plan.sizes.refused"));
            return false;
        } catch (RuntimeException ex) {
            UiErrors.show(this, ex);
            return false;
        }
        refreshAreas();
        return true;
    }

    /** Two fields and a step of a decimetre, as the plan's size is given (#90). */
    private static BigDecimalField planField(String label) {
        BigDecimalField field = new BigDecimalField(label);
        field.addClassName("rootology-plan-field");
        return field;
    }

    /** A length in metres in the page's language, without trailing zeros ("8", "0,4"). */
    private String metres(Number value) {
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(NoteText.locale(this));
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    /** One card per area (#85): its kind, soil and sun as icons, what grows there, and the actions. */
    private void showAreas() {
        if (garden == null) {
            areas = List.of();
            areaCards.removeAll();
            areaCards.setVisible(false);
            noAreas.setVisible(false);
            return;
        }
        areas = gardens.plots(spaceId, login, garden.id());
        areaCards.removeAll();
        areas.forEach(area -> areaCards.add(areaCard(area)));
        areaCards.setVisible(!areas.isEmpty());
        noAreas.setVisible(areas.isEmpty());
        // With no area the empty state carries the one button to add it (#81).
        addArea.setVisible(contributor && !areas.isEmpty());
    }

    private Div areaCard(PlotSummary area) {
        Div card = new Div();
        card.addClassName("rootology-area-card");
        card.getElement().setAttribute("data-area", Long.toString(area.id()));
        PlanArea placed = placedArea(area.id());
        card.getElement().getClassList().set("rootology-area-card-selected", area.id() == selectedArea);

        H4 name = new H4(AreaKindIcons.labelled(area.kind(), this), new Span(area.name()));
        name.addClassName("rootology-area-name");
        Div head = new Div(name);
        head.addClassName("rootology-area-head");
        if (contributor) {
            Div actions = new Div(
                    RowActions.edit(getTranslation("common.edit"), e -> areaDialog(area)),
                    RowActions.delete(getTranslation("common.delete"), e -> RowActions.confirmDelete(
                            getTranslation("garden.plot.delete.title"),
                            getTranslation("garden.plot.delete.text", area.name()),
                            null, getTranslation("garden.plot.delete.button"), getTranslation("common.cancel"),
                            () -> act(() -> gardens.deletePlot(spaceId, login, area.id())))));
            if (placed != null) {
                // Back off the plan, with the toast that puts it where it was (#90).
                actions.add(RowActions.remove(getTranslation("plan.take.off"), e -> takeOffPlan(area, placed)));
            }
            actions.addClassName("rootology-area-actions");
            head.add(actions);
        }
        card.add(head);

        Div facts = new Div();
        facts.addClassName("rootology-area-facts");
        facts.add(fact(VaadinIcon.FLASK, getTranslation("soil." + area.soil().name()), null));
        if (area.directSun() != null) {
            facts.add(fact(sunIcon(area.directSun()), getTranslation("directsun." + area.directSun().name()), null));
        }
        BigDecimal surface = surface(area, placed);
        if (surface != null) {
            facts.add(fact(VaadinIcon.RESIZE_H, getTranslation("garden.area"), squareMetres(surface) + " m²"));
        }
        card.add(facts);
        if (placed != null) {
            // A tap on the card marks its box on the plan (#90).
            card.addClickListener(e -> selectFromCard(area.id()));
        }

        List<PlantingSummary> all = gardens.plantings(spaceId, login, area.id());
        Div chips = new Div();
        chips.addClassName("rootology-chips");
        current(all).forEach(planting -> chips.add(chip(area, planting)));
        if (chips.getComponentCount() > 0) {
            card.add(chips);
        }

        if (contributor) {
            Button plant = new Button(getTranslation("garden.sow"), VaadinIcon.PLUS.create(),
                    e -> plantingDialog(area));
            plant.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            card.add(plant);
        }

        List<PlantingSummary> past = past(all);
        if (!past.isEmpty()) {
            Div rows = new Div();
            rows.addClassName("rootology-past-list");
            past.forEach(planting -> rows.add(pastRow(planting)));
            // Folded away (#85): the crop that grew here before is a record, not the decision of the day.
            Details grown = new Details(getTranslation("area.past", past.size()), rows);
            grown.addClassName("rootology-area-past");
            card.add(grown);
        }
        return card;
    }

    /** What grew here before (#85): crop, when it went in and when it came out, with the row's own delete. */
    private Div pastRow(PlantingSummary planting) {
        Div row = new Div();
        row.addClassName("rootology-past-row");
        row.add(new Span(cropName(planting.cropId(), planting.cropName())));
        String dates = getTranslation("area.past.dates",
                DateText.fullDate(planting.plantedOn(), NoteText.locale(this)),
                DateText.fullDate(planting.endedOn(), NoteText.locale(this)));
        Span span = new Span(DateText.date(planting.plantedOn(), this) + " → "
                + DateText.date(planting.endedOn(), this));
        span.addClassName("rootology-past-dates");
        span.getElement().setAttribute("title", dates);
        span.getElement().setAttribute("aria-label", dates);
        row.add(span);
        if (contributor) {
            Button delete = RowActions.delete(getTranslation("common.delete"), e -> deletePlanting(planting));
            delete.addClassName("rootology-past-action");
            row.add(delete);
        }
        return row;
    }

    /** Deleting a planting entered by mistake (#85): it asks first, since the rotation will not see it again. */
    private void deletePlanting(PlantingSummary planting) {
        RowActions.confirmDelete(getTranslation("garden.planting.delete.title"),
                getTranslation("garden.planting.delete.text", cropName(planting.cropId(), planting.cropName()),
                        DateText.date(planting.plantedOn(), this)),
                null, getTranslation("garden.planting.delete.button"), getTranslation("common.cancel"),
                () -> act(() -> gardens.deletePlanting(spaceId, login, planting.id())));
    }

    /** One value of the card: its icon with the word as tooltip and aria label, and the value when there is one. */
    private static Div fact(VaadinIcon icon, String label, String value) {
        Icon drawing = icon.create();
        drawing.addClassName("rootology-fact-icon");
        drawing.setTooltipText(label);
        drawing.getElement().setAttribute("aria-label", label);
        drawing.getElement().setAttribute("role", "img");
        Div row = new Div(drawing);
        if (value != null) {
            row.add(new Span(value));
        }
        row.addClassName("rootology-fact");
        return row;
    }

    private static VaadinIcon sunIcon(DirectSun sun) {
        return switch (sun) {
            case FULL_SUN -> VaadinIcon.SUN_O;
            case PART_SHADE -> VaadinIcon.CLOUD_O;
            case SHADE -> VaadinIcon.UMBRELLA;
        };
    }

    /** The crops still growing in the area, the most recently planted first. */
    static List<PlantingSummary> current(List<PlantingSummary> plantings) {
        return newestFirst(plantings, p -> p.endedOn() == null);
    }

    /** The plantings already over in the area (#85), the most recently planted first. */
    static List<PlantingSummary> past(List<PlantingSummary> plantings) {
        return newestFirst(plantings, p -> p.endedOn() != null);
    }

    private static List<PlantingSummary> newestFirst(List<PlantingSummary> plantings,
                                                     java.util.function.Predicate<PlantingSummary> keeps) {
        return plantings.stream().filter(keeps)
                .sorted(java.util.Comparator.comparing(PlantingSummary::plantedOn)
                        .thenComparingLong(PlantingSummary::id).reversed())
                .toList();
    }

    /** A surface in the page's language, without trailing zeros ("20 m²", "6,5 m²"). */
    private String squareMetres(Number value) {
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(NoteText.locale(this));
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    /** One crop of the area as a chip: the crop, how long it has been there, and the harvest (#85). */
    private Div chip(PlotSummary area, PlantingSummary planting) {
        Div chip = new Div();
        chip.addClassName("rootology-grow-chip");
        chip.getElement().setAttribute("title", DateText.fullDate(planting.plantedOn(), NoteText.locale(this)));
        Span crop = new Span(cropName(planting.cropId(), planting.cropName()));
        Span days = new Span(getTranslation("area.days",
                ChronoUnit.DAYS.between(planting.plantedOn(), LocalDate.now(DateText.ITALY))));
        days.addClassName("rootology-grow-days");
        chip.add(crop, days);
        if (contributor) {
            chip.add(RowActions.approve(getTranslation("garden.harvested"), e -> harvestDialog(planting)));
            // A planting put in by mistake is deleted, never harvested (#85): a harvest would count in the rotation.
            chip.add(RowActions.delete(getTranslation("common.delete"), e -> deletePlanting(planting)));
        }
        return chip;
    }

    /**
     * A frost day in the forecast is the only weather this page shows (#84): one line that opens the garden
     * home, where the coming days are. MET Norway is credited wherever its data appears.
     */
    private void showFrost(GardenSummary garden) {
        frostMark.removeAll();
        frostMark.setVisible(false);
        if (garden.latitude() == null || garden.longitude() == null) {
            return;
        }
        weather.around(garden.latitude(), garden.longitude()).ifPresent(forecast -> {
            var days = DailyForecast.of(forecast, GardenHomeView.ITALY, LocalDate.now(GardenHomeView.ITALY), 7);
            WeatherCard.firstFrost(days).ifPresent(frost -> {
                RouterLink link = frostLink(frost);
                Span credit = new Span(getTranslation("weather.credit"));
                credit.addClassName("rootology-areas-frost-credit");
                frostMark.add(link, credit);
                frostMark.setVisible(true);
            });
        });
    }

    private RouterLink frostLink(DailyForecast frost) {
        Locale locale = NoteText.locale(this);
        String day = frost.day().getDayOfWeek().getDisplayName(TextStyle.SHORT, locale) + " "
                + frost.day().getDayOfMonth();
        String text = getTranslation("plots.frost", day);
        Icon icon = VaadinIcon.ASTERISK.create();
        icon.getElement().setAttribute("aria-hidden", "true");
        RouterLink link = new RouterLink("", GardenHomeView.class, new RouteParameters(Map.of(
                Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(garden.id()))));
        link.add(icon, new Span(text));
        link.addClassName("rootology-frost-marker");
        link.getElement().setAttribute("aria-label", text);
        return link;
    }

    /** Creates or edits this garden: the overview adds new ones, this page edits the one it works in. */
    private void gardenDialog() {
        new GardenDialog(geoNames, climateCells, (name, town, latitude, longitude, countryCode, elevationM) ->
                gardens.saveGarden(spaceId, login, garden == null ? null : garden.id(), name, town, latitude,
                        longitude, countryCode, elevationM)).open(garden, this::showGarden);
    }

    /**
     * The area in two steps (#85): name, kind and soil first; the declared exposure (#54) behind Details,
     * with the direct sun as three icon buttons.
     */
    private void areaDialog(PlotSummary existing) {
        GardenSummary garden = this.garden;
        if (garden == null) {
            Toasts.info(getTranslation("garden.first"));
            return;
        }
        TextField name = new TextField(getTranslation("garden.name"));
        ComboBox<PlotKind> kind = new ComboBox<>(getTranslation("garden.kind"), PlotKind.values());
        kind.setItemLabelGenerator(k -> getTranslation("plotkind." + k.name()));
        ComboBox<Soil> soil = new ComboBox<>(getTranslation("garden.soil"), Soil.values());
        soil.setItemLabelGenerator(k -> getTranslation("soil." + k.name()));
        BigDecimalField area = new BigDecimalField(getTranslation("garden.area.field"));
        // The declared exposure: all optional, so they can be cleared.
        ComboBox<Orientation> orientation = new ComboBox<>(getTranslation("garden.orientation"), Orientation.values());
        orientation.setItemLabelGenerator(o -> getTranslation("orientation." + o.name()));
        orientation.setClearButtonVisible(true);
        SunToggle sun = new SunToggle(this);
        ComboBox<Wind> wind = new ComboBox<>(getTranslation("garden.wind"), Wind.values());
        wind.setItemLabelGenerator(w -> getTranslation("wind." + w.name()));
        wind.setClearButtonVisible(true);
        IntegerField floor = new IntegerField(getTranslation("garden.floor"));
        floor.setMin(0);
        floor.setMax(200);
        floor.setClearButtonVisible(true);
        kind.setValue(PlotKind.GROUND);
        soil.setValue(Soil.UNKNOWN);
        if (existing != null) {
            name.setValue(existing.name());
            kind.setValue(existing.kind());
            soil.setValue(existing.soil());
            area.setValue(existing.areaM2());
            orientation.setValue(existing.orientation());
            sun.setValue(existing.directSun());
            wind.setValue(existing.wind());
            floor.setValue(existing.floor());
        }
        Details details = new Details(getTranslation("garden.details"),
                new FormLayout(orientation, sun, wind, floor));
        details.addClassName("rootology-area-details");
        Dialog dialog = new Dialog(getTranslation(existing == null ? "garden.addplot" : "garden.editplot"));
        dialog.add(new FormLayout(name, kind, soil, area), details);
        dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()),
                new Button(getTranslation("common.save"), e -> {
            try {
                gardens.savePlot(spaceId, login, garden.id(), existing == null ? null : existing.id(),
                        name.getValue(), kind.getValue(), soil.getValue(), area.getValue(),
                        orientation.getValue(), sun.getValue(), wind.getValue(), floor.getValue());
                dialog.close();
                refreshAreas();
            } catch (RuntimeException ex) {
                UiErrors.show(this, ex);
            }
        }));
        dialog.open();
    }

    /** Today at the latest, and never before the day it was sown or planted (#85). */
    static boolean harvestDateValid(LocalDate day, LocalDate plantedOn, LocalDate today) {
        return day != null && !day.isAfter(today) && !day.isBefore(plantedOn);
    }

    /** Harvested on a day the person chooses (#85), with a toast that can undo it while it is shown. */
    private void harvestDialog(PlantingSummary planting) {
        LocalDate today = LocalDate.now(DateText.ITALY);
        DatePicker date = new DatePicker(getTranslation("harvest.date"), today);
        date.setMin(planting.plantedOn());
        date.setMax(today);
        Paragraph problem = new Paragraph(getTranslation("harvest.date.range"));
        problem.addClassNames(com.vaadin.flow.theme.lumo.LumoUtility.TextColor.ERROR);
        problem.setVisible(false);
        Dialog dialog = new Dialog(getTranslation("harvest.title", cropName(planting.cropId(), planting.cropName())));
        dialog.add(date, problem);
        dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()),
                new Button(getTranslation("common.save"), e -> {
            if (!harvestDateValid(date.getValue(), planting.plantedOn(), today)) {
                problem.setVisible(true);
                return;
            }
            try {
                gardens.endPlanting(spaceId, login, planting.id(), date.getValue());
                dialog.close();
                refreshAreas();
                toastHarvested(planting);
            } catch (RuntimeException ex) {
                UiErrors.show(this, ex);
            }
        }));
        dialog.open();
    }

    private void toastHarvested(PlantingSummary planting) {
        Toasts.withUndo(getTranslation("harvest.done", cropName(planting.cropId(), planting.cropName())),
                getTranslation("common.undo"), () -> {
                    try {
                        gardens.undoEndPlanting(spaceId, login, planting.id());
                        refreshAreas();
                    } catch (RuntimeException ex) {
                        UiErrors.show(this, ex);
                    }
                });
    }

    private void plantingDialog(PlotSummary area) {
        ComboBox<Crop> crop = new ComboBox<>(getTranslation("garden.crop"), crops.all());
        crop.setItemLabelGenerator(c -> cropName(c.id(), c.italianName()));
        ComboBox<PlantingMethod> method = new ComboBox<>(getTranslation("garden.method"), PlantingMethod.values());
        method.setItemLabelGenerator(m -> getTranslation("method." + m.name()));
        method.setValue(PlantingMethod.TRANSPLANTED);
        DatePicker date = new DatePicker(getTranslation("garden.date"), LocalDate.now());
        TextField notes = new TextField(getTranslation("garden.notes.field"));
        Paragraph rotationWarning = new Paragraph();
        rotationWarning.addClassNames(com.vaadin.flow.theme.lumo.LumoUtility.TextColor.ERROR);
        Runnable checkRotation = () -> rotationWarning.setText(crop.getValue() == null || date.getValue() == null ? ""
                : gardens.rotationWarning(spaceId, login, area.id(), crop.getValue().id(), date.getValue())
                .map(w -> getTranslation("garden.rotation", cropName(crop.getValue().id(), crop.getValue().italianName()),
                        cropName(w.previous().cropId(), w.previous().cropName()),
                        DateText.date(w.previous().plantedOn(), this),
                        w.family(), w.previous().plotName(), w.rule())).orElse(""));
        crop.addValueChangeListener(e -> checkRotation.run());
        date.addValueChangeListener(e -> checkRotation.run());
        Dialog dialog = new Dialog(getTranslation("garden.sowin", area.name()));
        dialog.add(new FormLayout(crop, method, date, notes), rotationWarning);
        dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()),
                new Button(getTranslation("common.save"), e -> {
            try {
                gardens.plant(spaceId, login, area.id(), crop.getValue() == null ? null : crop.getValue().id(),
                        method.getValue(), date.getValue(), notes.getValue());
                dialog.close();
                refreshAreas();
            } catch (RuntimeException ex) {
                UiErrors.show(this, ex);
            }
        }));
        dialog.open();
    }

    /** Runs an action, shows its error if any, and refreshes the garden's areas. */
    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            UiErrors.show(this, ex);
        }
        refreshAreas();
    }

    /** Where an area sits on the plan, or null when it is not placed (#90). */
    private PlanArea placedArea(long plotId) {
        return plan == null ? null : plan.areas().stream()
                .filter(area -> area.plotId() == plotId && area.placed()).findFirst().orElse(null);
    }

    /**
     * An area's surface (#90): the one the person declared, or, on the plan, the box's width × depth, since the
     * plan's size is the one they drew. Null when neither is known.
     */
    private static BigDecimal surface(PlotSummary area, PlanArea placed) {
        if (area.areaM2() != null) {
            return area.areaM2();
        }
        return placed == null ? null : placed.width().multiply(placed.depth());
    }

    /** Takes an area off the plan and keeps the area (#90), with a toast that puts it back where it was. */
    private void takeOffPlan(PlotSummary area, PlanArea placed) {
        try {
            gardens.unplaceArea(spaceId, login, area.id());
        } catch (RuntimeException ex) {
            UiErrors.show(this, ex);
            return;
        }
        refreshAreas();
        Toasts.withUndo(getTranslation("plan.taken.off", area.name()), getTranslation("common.undo"), () -> {
            try {
                gardens.placeArea(spaceId, login, area.id(), placed.x(), placed.y(), placed.width(),
                        placed.depth());
            } catch (RuntimeException ex) {
                UiErrors.show(this, ex);
            }
            refreshAreas();
        });
    }

    /** The plan's size line, for the tests (#90). */
    String planSizeText() {
        return planSize.getText();
    }

    /** The plan on the page, for the tests (#90). */
    GardenPlanBoard board() {
        return board;
    }

    /** The area whose card is marked, 0 for none, for the tests (#90). */
    long selectedArea() {
        return selectedArea;
    }

    /** Whether the invitation to draw the plan is on the page, for the tests (#90). */
    boolean planInvited() {
        return planInvite.isVisible();
    }

    /** Whether the plan itself is on the page, for the tests (#90). */
    boolean planShown() {
        return planHead.isVisible();
    }    /** The areas still to place, by id, for the tests (#90). */
    List<Long> unplacedOnPage() {
        return unplaced.getChildren().filter(child -> child instanceof Button)
                .map(button -> Long.parseLong(button.getElement().getAttribute("data-area")))
                .toList();
    }

    /** A crop's name in the page's language: Italian, or English for an English page. */
    private String cropName(String cropId, String fallback) {
        boolean english = getLocale() != null && getLocale().getLanguage().equals("en");
        return crops.byId(cropId).map(c -> english ? c.englishName() : c.italianName()).orElse(fallback);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("garden.plots") + " · Rootology";
    }

    /** The direct sun as three icon buttons (#85): one chosen at a time, and a second click clears it. */
    private static final class SunToggle extends Div {

        private final Map<DirectSun, Button> buttons = new LinkedHashMap<>();
        private DirectSun value;

        SunToggle(Component texts) {
            addClassName("rootology-sun-toggle");
            // The three buttons are one choice (#85): the group carries the word the buttons have in common.
            getElement().setAttribute("role", "group");
            getElement().setAttribute("aria-label", texts.getTranslation("garden.sun"));
            for (DirectSun sun : DirectSun.values()) {
                Icon icon = switch (sun) {
                    case FULL_SUN -> VaadinIcon.SUN_O.create();
                    case PART_SHADE -> VaadinIcon.CLOUD_O.create();
                    case SHADE -> VaadinIcon.UMBRELLA.create();
                };
                icon.getElement().setAttribute("aria-hidden", "true");
                String word = texts.getTranslation("directsun." + sun.name());
                Button button = new Button(icon, e -> setValue(value == sun ? null : sun));
                button.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
                button.setAriaLabel(word);
                button.setTooltipText(word);
                buttons.put(sun, button);
                add(button);
            }
            show();
        }

        DirectSun getValue() {
            return value;
        }

        void setValue(DirectSun value) {
            this.value = value;
            show();
        }

        private void show() {
            buttons.forEach((sun, button) -> {
                button.removeClassName("rootology-sun-chosen");
                if (sun == value) {
                    button.addClassName("rootology-sun-chosen");
                }
            });
        }
    }
}
