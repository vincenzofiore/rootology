package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.facts.Crop;
import io.github.vincenzofiore.rootology.facts.CropCatalog;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.geocoding.GeoNames;
import io.github.vincenzofiore.rootology.geocoding.Place;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlantingSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlotSummary;
import io.github.vincenzofiore.rootology.garden.PlantingMethod;
import io.github.vincenzofiore.rootology.garden.PlotKind;
import io.github.vincenzofiore.rootology.garden.Soil;
import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.util.List;

/** The current space's gardens: plots, and what is sown or planted in each. */
@Route(value = "garden", layout = MainLayout.class)
@PageTitle("Garden · Rootology")
@PermitAll
public class GardenView extends VerticalLayout {

    private final GardenService gardens;
    private final CropCatalog crops;
    private final GeoNames geoNames;
    private final long spaceId;
    private final String login;

    private final ComboBox<GardenSummary> gardenBox = new ComboBox<>("Garden");
    private final Paragraph gardenInfo = new Paragraph();
    private final Grid<PlotSummary> plotGrid = new Grid<>();
    private final Grid<PlantingSummary> plantingGrid = new Grid<>();
    private final H4 plantingTitle = new H4("Select a plot");

    public GardenView(GardenService gardens, CropCatalog crops, GeoNames geoNames, CurrentUser currentUser) {
        this.gardens = gardens;
        this.crops = crops;
        this.geoNames = geoNames;
        this.spaceId = currentUser.space().id();
        this.login = currentUser.login();

        gardenBox.setItemLabelGenerator(GardenSummary::name);
        gardenBox.addValueChangeListener(e -> showGarden(e.getValue()));
        Button newGarden = new Button("New garden", e -> gardenDialog(null));
        Button editGarden = new Button("Edit garden", e -> gardenDialog(gardenBox.getValue()));
        // A guest only looks (#33): every button that changes the garden is hidden.
        boolean contributor = currentUser.canContribute();
        newGarden.setVisible(contributor);
        editGarden.setVisible(contributor);
        HorizontalLayout top = new HorizontalLayout(gardenBox, newGarden, editGarden);
        top.setDefaultVerticalComponentAlignment(Alignment.END);

        plotGrid.addColumn(PlotSummary::name).setHeader("Plot");
        plotGrid.addColumn(p -> p.kind().name()).setHeader("Kind");
        plotGrid.addColumn(p -> p.soil().name()).setHeader("Soil");
        plotGrid.addColumn(p -> p.areaM2() == null ? "" : p.areaM2() + " m²").setHeader("Area");
        plotGrid.addComponentColumn(p -> new HorizontalLayout(
                new Button("Edit", e -> plotDialog(p)),
                new Button("Delete", e -> act(() -> gardens.deletePlot(spaceId, login, p.id())))))
                .setVisible(contributor);
        plotGrid.setAllRowsVisible(true);
        plotGrid.asSingleSelect().addValueChangeListener(e -> showPlantings(e.getValue()));

        plantingGrid.addColumn(PlantingSummary::cropName).setHeader("Crop");
        plantingGrid.addColumn(p -> p.method().name()).setHeader("Method");
        plantingGrid.addColumn(PlantingSummary::plantedOn).setHeader("Since");
        plantingGrid.addColumn(p -> p.endedOn() == null ? "growing" : "ended " + p.endedOn()).setHeader("Status");
        plantingGrid.addColumn(p -> p.notes() == null ? "" : p.notes()).setHeader("Notes");
        plantingGrid.addComponentColumn(p -> new HorizontalLayout(
                new Button("Harvested today", e -> act(() ->
                        gardens.endPlanting(spaceId, login, p.id(), LocalDate.now()))),
                new Button("Delete", e -> act(() -> gardens.deletePlanting(spaceId, login, p.id())))))
                .setVisible(contributor);
        plantingGrid.setAllRowsVisible(true);

        Button addPlot = new Button("Add plot", e -> plotDialog(null));
        Button addPlanting = new Button("Sow or plant", e -> plantingDialog());
        addPlot.setVisible(contributor);
        addPlanting.setVisible(contributor);

        add(new H3("My garden"), top, gardenInfo,
                new HorizontalLayout(new H4("Plots"), addPlot), plotGrid,
                new HorizontalLayout(plantingTitle, addPlanting), plantingGrid);
        reloadGardens(null);
    }

    private void reloadGardens(Long select) {
        List<GardenSummary> all = gardens.gardens(spaceId, login);
        gardenBox.setItems(all);
        all.stream().filter(g -> select == null || g.id() == select).findFirst().ifPresentOrElse(
                gardenBox::setValue, () -> showGarden(null));
    }

    private void showGarden(GardenSummary garden) {
        if (garden == null) {
            gardenInfo.setText("No garden yet: create one with \"New garden\".");
            plotGrid.setItems(List.of());
            showPlantings(null);
            return;
        }
        String where = garden.town() == null ? "no town" : garden.town();
        String coords = garden.latitude() == null ? "" : " (" + garden.latitude() + ", " + garden.longitude() + ")";
        String inCountry = garden.countryCode() == null ? "" : ", " + garden.countryCode();
        gardenInfo.setText(where + inCountry + coords);
        plotGrid.setItems(gardens.plots(spaceId, login, garden.id()));
        showPlantings(null);
    }

    private static java.math.BigDecimal coarse(double degrees) {
        return java.math.BigDecimal.valueOf(degrees).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void showPlantings(PlotSummary plot) {
        plantingTitle.setText(plot == null ? "Select a plot" : "In " + plot.name());
        plantingGrid.setItems(plot == null ? List.of() : gardens.plantings(spaceId, login, plot.id()));
    }

    private void gardenDialog(GardenSummary existing) {
        TextField name = new TextField("Name");
        TextField town = new TextField("Town");
        BigDecimalField latitude = new BigDecimalField("Latitude (optional)");
        BigDecimalField longitude = new BigDecimalField("Longitude (optional)");
        TextField country = new TextField("Country (ISO code, optional)");
        country.setMaxLength(2);
        country.setPattern("[A-Za-z]{2}");
        if (existing != null) {
            name.setValue(existing.name());
            town.setValue(existing.town() == null ? "" : existing.town());
            latitude.setValue(existing.latitude());
            longitude.setValue(existing.longitude());
            country.setValue(existing.countryCode() == null ? "" : existing.countryCode());
        }

        // A place found by name fills in the town, the coordinates and the country (#30).
        String language = getLocale() == null ? "en" : getLocale().getLanguage();
        ComboBox<Place> place = new ComboBox<>("Find the place");
        place.setPlaceholder("Type at least 3 letters");
        place.setItemLabelGenerator(Place::label);
        place.setItems(query -> geoNames.search(query.getFilter().orElse(""), language).stream()
                .skip(query.getOffset()).limit(query.getLimit()));
        place.addValueChangeListener(e -> {
            Place p = e.getValue();
            if (p != null) {
                town.setValue(p.name());
                latitude.setValue(coarse(p.latitude()));
                longitude.setValue(coarse(p.longitude()));
                country.setValue(p.countryCode() == null ? "" : p.countryCode());
            }
        });
        place.setVisible(geoNames.enabled());

        // The browser asks the person first; it works on localhost and over HTTPS only.
        Button here = new Button("Use my position", e -> getElement().executeJs("""
                return new Promise((resolve, reject) => navigator.geolocation
                    ? navigator.geolocation.getCurrentPosition(
                        p => resolve(p.coords.latitude + ',' + p.coords.longitude),
                        err => reject(err.message), {timeout: 10000})
                    : reject('not supported'))""")
                .then(String.class, position -> {
                    String[] parts = position.split(",");
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);
                    latitude.setValue(coarse(lat));
                    longitude.setValue(coarse(lon));
                    // The nearest town and its country, as when a place is chosen from the search.
                    geoNames.nearestPlace(lat, lon, language).ifPresent(p -> {
                        town.setValue(p.name());
                        country.setValue(p.countryCode());
                    });
                }, error -> Notification.show("Position not available: " + error)));

        Dialog dialog = new Dialog(existing == null ? "New garden" : "Edit garden");
        dialog.add(new FormLayout(name, place, town, latitude, longitude, country), here,
                new Paragraph("Only a coarse location is kept: coordinates are rounded to about 1 km. "
                        + "Sowing calendars cover Italy only for now; the weather works everywhere."
                        + (geoNames.enabled() ? " Place search by GeoNames (CC BY 4.0)." : "")));
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), new Button("Save", e -> {
            try {
                String countryCode = country.getValue().trim().toUpperCase(java.util.Locale.ROOT);
                if ((countryCode.isEmpty() || town.getValue().isBlank())
                        && latitude.getValue() != null && longitude.getValue() != null) {
                    // Typed coordinates: fill what is missing from the nearest town; without GeoNames, save anyway.
                    var nearest = geoNames.nearestPlace(latitude.getValue().doubleValue(),
                            longitude.getValue().doubleValue(), language);
                    if (nearest.isPresent()) {
                        if (countryCode.isEmpty()) {
                            countryCode = nearest.get().countryCode();
                        }
                        if (town.getValue().isBlank()) {
                            town.setValue(nearest.get().name());
                        }
                    }
                }
                GardenSummary saved = gardens.saveGarden(spaceId, login, existing == null ? null : existing.id(),
                        name.getValue(), town.getValue(), latitude.getValue(), longitude.getValue(), countryCode);
                dialog.close();
                reloadGardens(saved.id());
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage());
            }
        }));
        dialog.open();
    }

    private void plotDialog(PlotSummary existing) {
        GardenSummary garden = gardenBox.getValue();
        if (garden == null) {
            Notification.show("Create a garden first");
            return;
        }
        TextField name = new TextField("Name");
        ComboBox<PlotKind> kind = new ComboBox<>("Kind", PlotKind.values());
        ComboBox<Soil> soil = new ComboBox<>("Soil", Soil.values());
        BigDecimalField area = new BigDecimalField("Area in m² (optional)");
        kind.setValue(PlotKind.GROUND);
        soil.setValue(Soil.UNKNOWN);
        if (existing != null) {
            name.setValue(existing.name());
            kind.setValue(existing.kind());
            soil.setValue(existing.soil());
            area.setValue(existing.areaM2());
        }
        Dialog dialog = new Dialog(existing == null ? "Add plot" : "Edit plot");
        dialog.add(new FormLayout(name, kind, soil, area));
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), new Button("Save", e -> {
            try {
                gardens.savePlot(spaceId, login, garden.id(), existing == null ? null : existing.id(),
                        name.getValue(), kind.getValue(), soil.getValue(), area.getValue());
                dialog.close();
                showGarden(garden);
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage());
            }
        }));
        dialog.open();
    }

    private void plantingDialog() {
        PlotSummary plot = plotGrid.asSingleSelect().getValue();
        if (plot == null) {
            Notification.show("Select a plot first");
            return;
        }
        ComboBox<Crop> crop = new ComboBox<>("Crop", crops.all());
        crop.setItemLabelGenerator(c -> c.italianName() + " (" + c.englishName() + ")");
        ComboBox<PlantingMethod> method = new ComboBox<>("Method", PlantingMethod.values());
        method.setValue(PlantingMethod.TRANSPLANTED);
        DatePicker date = new DatePicker("Date", LocalDate.now());
        TextField notes = new TextField("Notes (optional)");
        Paragraph rotationWarning = new Paragraph();
        rotationWarning.getStyle().set("color", "var(--vaadin-error-text-color, #b00020)");
        Runnable checkRotation = () -> rotationWarning.setText(crop.getValue() == null || date.getValue() == null ? ""
                : gardens.rotationWarning(spaceId, login, plot.id(), crop.getValue().id(), date.getValue())
                .map(w -> w.reason() + " (inferred by rule " + w.rule() + ")").orElse(""));
        crop.addValueChangeListener(e -> checkRotation.run());
        date.addValueChangeListener(e -> checkRotation.run());
        Dialog dialog = new Dialog("Sow or plant in " + plot.name());
        dialog.add(new FormLayout(crop, method, date, notes), rotationWarning);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), new Button("Save", e -> {
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
        showGarden(gardenBox.getValue());
        if (selected != null) {
            plotGrid.asSingleSelect().setValue(selected);
        }
    }
}
