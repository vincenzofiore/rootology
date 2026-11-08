package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
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
import io.github.vincenzofiore.rootology.facts.Crop;
import io.github.vincenzofiore.rootology.facts.CropCatalog;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlantingSummary;
import io.github.vincenzofiore.rootology.garden.DirectSun;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlotSummary;
import io.github.vincenzofiore.rootology.garden.Orientation;
import io.github.vincenzofiore.rootology.garden.PlantingMethod;
import io.github.vincenzofiore.rootology.garden.PlotKind;
import io.github.vincenzofiore.rootology.garden.Soil;
import io.github.vincenzofiore.rootology.garden.Wind;
import io.github.vincenzofiore.rootology.geocoding.GeoNames;
import io.github.vincenzofiore.rootology.weather.DailyForecast;
import io.github.vincenzofiore.rootology.weather.WeatherService;
import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.util.List;

/** One garden of the space: its plots, and what is sown or planted in each (#56). */
@Route(value = "spaces/:spaceId/gardens/:gardenId/plots", layout = MainLayout.class)
@PermitAll
public class PlotsView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private final GardenService gardens;
    private final CropCatalog crops;
    private final GeoNames geoNames;
    private final WeatherService weather;
    private final Div weatherCard = new Div();
    private final CurrentUser currentUser;
    private final String login;
    /** The page's own context (#56), read from its route. */
    private long spaceId;
    private GardenSummary garden;

    private final Paragraph gardenInfo = new Paragraph();
    private final Grid<PlotSummary> plotGrid = new Grid<>();
    private final Grid<PlantingSummary> plantingGrid = new Grid<>();
    private final H4 plantingTitle = new H4();
    private final Button editGarden = new Button(getTranslation("garden.edit"));
    private final HorizontalLayout top = new HorizontalLayout();
    private final Button addPlot = new Button(getTranslation("garden.addplot"));
    private final Button addPlanting = new Button(getTranslation("garden.sow"));
    /** The actions that need an OWNER or MEMBER (#33). */
    private Grid.Column<PlotSummary> plotActions;
    private Grid.Column<PlantingSummary> plantingActions;

    public PlotsView(GardenService gardens, CropCatalog crops, GeoNames geoNames, WeatherService weather,
                     CurrentUser currentUser) {
        this.weather = weather;
        this.gardens = gardens;
        this.crops = crops;
        this.geoNames = geoNames;
        this.currentUser = currentUser;
        this.login = currentUser.login();

        plantingTitle.setText(getTranslation("garden.selectplot"));
        editGarden.addClickListener(e -> gardenDialog());
        // A guest only looks (#33): every action is hidden, and the role is known in beforeEnter.
        top.add(editGarden);
        top.setDefaultVerticalComponentAlignment(Alignment.END);

        plotGrid.addColumn(PlotSummary::name).setHeader(getTranslation("garden.plot"));
        plotGrid.addColumn(p -> getTranslation("plotkind." + p.kind().name())).setHeader(getTranslation("garden.kind"));
        plotGrid.addColumn(p -> getTranslation("soil." + p.soil().name())).setHeader(getTranslation("garden.soil"));
        plotGrid.addColumn(this::exposure).setHeader(getTranslation("garden.exposure")).setAutoWidth(true);
        plotGrid.addColumn(p -> p.areaM2() == null ? "" : p.areaM2() + " m²").setHeader(getTranslation("garden.area"));
        plotActions = plotGrid.addComponentColumn(p -> new HorizontalLayout(
                new Button(getTranslation("common.edit"), e -> plotDialog(p)),
                new Button(getTranslation("common.delete"), e -> act(() -> gardens.deletePlot(spaceId, login, p.id())))))
                .setAutoWidth(true);
        plotGrid.setAllRowsVisible(true);
        plotGrid.asSingleSelect().addValueChangeListener(e -> showPlantings(e.getValue()));

        plantingGrid.addColumn(p -> cropName(p.cropId(), p.cropName())).setHeader(getTranslation("garden.crop"));
        plantingGrid.addColumn(p -> getTranslation("method." + p.method().name())).setHeader(getTranslation("garden.method"));
        plantingGrid.addColumn(PlantingSummary::plantedOn).setHeader(getTranslation("garden.since"));
        plantingGrid.addColumn(p -> p.endedOn() == null ? getTranslation("garden.growing")
                : getTranslation("garden.ended", p.endedOn().toString())).setHeader(getTranslation("garden.status"));
        plantingGrid.addColumn(p -> p.notes() == null ? "" : p.notes()).setHeader(getTranslation("garden.notes"));
        plantingActions = plantingGrid.addComponentColumn(p -> new HorizontalLayout(
                new Button(getTranslation("garden.harvested"), e -> act(() ->
                        gardens.endPlanting(spaceId, login, p.id(), LocalDate.now()))),
                new Button(getTranslation("common.delete"), e -> act(() -> gardens.deletePlanting(spaceId, login, p.id())))))
                .setAutoWidth(true);
        plantingGrid.setAllRowsVisible(true);

        addPlot.addClickListener(e -> plotDialog(null));
        addPlanting.addClickListener(e -> plantingDialog());
        showContributorActions(false);

        add(new H3(getTranslation("garden.title")), top, gardenInfo, weatherCard,
                new HorizontalLayout(new H4(getTranslation("garden.plots")), addPlot), plotGrid,
                new HorizontalLayout(plantingTitle, addPlanting), plantingGrid);
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
        addPlot.setVisible(contributor);
        addPlanting.setVisible(contributor);
        plotActions.setVisible(contributor);
        plantingActions.setVisible(contributor);
    }

    private void showGarden(GardenSummary garden) {
        this.garden = garden;
        if (garden == null) {
            weatherCard.removeAll();
            gardenInfo.setText(getTranslation("garden.none"));
            plotGrid.setItems(List.of());
            showPlantings(null);
            return;
        }
        String where = garden.town() == null ? getTranslation("garden.notown") : garden.town();
        String coords = garden.latitude() == null ? "" : " (" + garden.latitude() + ", " + garden.longitude() + ")";
        String inCountry = garden.countryCode() == null ? "" : ", " + garden.countryCode();
        gardenInfo.setText(where + inCountry + coords);
        showWeather(garden);
        plotGrid.setItems(gardens.plots(spaceId, login, garden.id()));
        showPlantings(null);
    }

    /** The coming week at the garden (#31); a hint to add coordinates, or nothing when there is no forecast. */
    private void showWeather(GardenSummary garden) {
        weatherCard.removeAll();
        if (garden.latitude() == null || garden.longitude() == null) {
            Paragraph hint = new Paragraph(getTranslation("weather.nocoordinates"));
            hint.addClassNames(com.vaadin.flow.theme.lumo.LumoUtility.TextColor.SECONDARY);
            weatherCard.add(hint);
            return;
        }
        weather.around(garden.latitude(), garden.longitude()).ifPresentOrElse(forecast -> {
            var days = DailyForecast.of(forecast, java.time.ZoneId.of("Europe/Rome"), LocalDate.now(), 7);
            if (!days.isEmpty()) {
                weatherCard.add(new WeatherCard(days, forecast.fetchedAt(), forecast.stale()));
            }
        }, () -> weatherCard.add(new Paragraph(getTranslation("note.FORECAST_UNAVAILABLE_FROST"))));
    }

    private void showPlantings(PlotSummary plot) {
        plantingTitle.setText(plot == null ? getTranslation("garden.selectplot") : getTranslation("garden.in", plot.name()));
        plantingGrid.setItems(plot == null ? List.of() : gardens.plantings(spaceId, login, plot.id()));
    }

    /** Creates or edits this garden: the overview adds new ones, this page edits the one it works in. */
    private void gardenDialog() {
        new GardenDialog(geoNames, (name, town, latitude, longitude, countryCode) ->
                gardens.saveGarden(spaceId, login, garden == null ? null : garden.id(), name, town, latitude,
                        longitude, countryCode)).open(garden, this::showGarden);
    }

    private void plotDialog(PlotSummary existing) {
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
        // The declared exposure (#54): all optional, so they can be cleared.
        ComboBox<Orientation> orientation = new ComboBox<>(getTranslation("garden.orientation"), Orientation.values());
        orientation.setItemLabelGenerator(o -> getTranslation("orientation." + o.name()));
        orientation.setClearButtonVisible(true);
        ComboBox<DirectSun> sun = new ComboBox<>(getTranslation("garden.sun"), DirectSun.values());
        sun.setItemLabelGenerator(d -> getTranslation("directsun." + d.name()));
        sun.setClearButtonVisible(true);
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
        Dialog dialog = new Dialog(getTranslation(existing == null ? "garden.addplot" : "garden.editplot"));
        dialog.add(new FormLayout(name, kind, soil, area, orientation, sun, wind, floor));
        dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()),
                new Button(getTranslation("common.save"), e -> {
            try {
                gardens.savePlot(spaceId, login, garden.id(), existing == null ? null : existing.id(),
                        name.getValue(), kind.getValue(), soil.getValue(), area.getValue(),
                        orientation.getValue(), sun.getValue(), wind.getValue(), floor.getValue());
                dialog.close();
                showGarden(garden);
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage());
            }
        }));
        dialog.open();
    }

    /** Orientation and direct sun as declared, when they are (#54). */
    private String exposure(PlotSummary plot) {
        List<String> parts = new java.util.ArrayList<>();
        if (plot.orientation() != null) {
            parts.add(getTranslation("orientation." + plot.orientation().name()));
        }
        if (plot.directSun() != null) {
            parts.add(getTranslation("directsun." + plot.directSun().name()));
        }
        return String.join(", ", parts);
    }

    private void plantingDialog() {
        PlotSummary plot = plotGrid.asSingleSelect().getValue();
        if (plot == null) {
            Notification.show(getTranslation("garden.plotfirst"));
            return;
        }
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
                : gardens.rotationWarning(spaceId, login, plot.id(), crop.getValue().id(), date.getValue())
                .map(w -> getTranslation("garden.rotation", cropName(crop.getValue().id(), crop.getValue().italianName()),
                        cropName(w.previous().cropId(), w.previous().cropName()), w.previous().plantedOn().toString(),
                        w.family(), w.previous().plotName(), w.rule())).orElse(""));
        crop.addValueChangeListener(e -> checkRotation.run());
        date.addValueChangeListener(e -> checkRotation.run());
        Dialog dialog = new Dialog(getTranslation("garden.sowin", plot.name()));
        dialog.add(new FormLayout(crop, method, date, notes), rotationWarning);
        dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()),
                new Button(getTranslation("common.save"), e -> {
            try {
                gardens.plant(spaceId, login, plot.id(), crop.getValue() == null ? null : crop.getValue().id(),
                        method.getValue(), date.getValue(), notes.getValue());
                dialog.close();
                showPlantings(plot);
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage());
            }
        }));
        dialog.open();
    }

    /** Runs an action, shows its error if any, and refreshes the current garden. */
    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
        PlotSummary selected = plotGrid.asSingleSelect().getValue();
        showGarden(garden);
        if (selected != null) {
            plotGrid.asSingleSelect().setValue(selected);
        }
    }

    /** A crop's name in the page's language: Italian, or English for an English page. */
    private String cropName(String cropId, String fallback) {
        boolean english = getLocale() != null && getLocale().getLanguage().equals("en");
        return crops.byId(cropId).map(c -> english ? c.englishName() : c.italianName()).orElse(fallback);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("garden.title") + " · Rootology";
    }
}
