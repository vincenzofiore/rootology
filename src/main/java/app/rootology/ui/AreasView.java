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
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
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
import app.rootology.garden.GardenViews.GardenSummary;
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
        showContributorActions(false);

        areaCards.addClassName("rootology-area-cards");
        noAreas.setVisible(false);
        frostMark.addClassName("rootology-areas-frost");
        frostMark.setVisible(false);
        add(new H3(getTranslation("garden.plots")), top, gardenInfo, frostMark, areaCards, noAreas);
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
            addArea.setVisible(false);
            return;
        }
        // Town and country are enough (#79): the exact position is the map's business.
        String where = garden.town() == null ? getTranslation("garden.notown") : garden.town();
        String inCountry = garden.countryCode() == null ? "" : ", " + garden.countryCode();
        gardenInfo.setText(where + inCountry);
        showFrost(garden);
        showAreas();
    }

    /** One card per area (#85): its kind, soil and sun as icons, what grows there, and the actions. */
    private void showAreas() {
        List<PlotSummary> areas = gardens.plots(spaceId, login, garden.id());
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
        if (area.areaM2() != null) {
            facts.add(fact(VaadinIcon.RESIZE_H, getTranslation("garden.area"), squareMetres(area.areaM2()) + " m²"));
        }
        card.add(facts);

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
            Notification.show(getTranslation("garden.first"));
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
                showAreas();
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
                showAreas();
                toastHarvested(planting);
            } catch (RuntimeException ex) {
                UiErrors.show(this, ex);
            }
        }));
        dialog.open();
    }

    private void toastHarvested(PlantingSummary planting) {
        Notification toast = new Notification();
        toast.setDuration(6000);
        toast.setPosition(Notification.Position.BOTTOM_CENTER);
        Button undo = new Button(getTranslation("common.undo"), e -> {
            try {
                gardens.undoEndPlanting(spaceId, login, planting.id());
                toast.close();
                showAreas();
            } catch (RuntimeException ex) {
                UiErrors.show(this, ex);
            }
        });
        undo.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout row = new HorizontalLayout(
                new Span(getTranslation("harvest.done", cropName(planting.cropId(), planting.cropName()))), undo);
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        toast.add(row);
        toast.open();
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
                showAreas();
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
        showAreas();
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
