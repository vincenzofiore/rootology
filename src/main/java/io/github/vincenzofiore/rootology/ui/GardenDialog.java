package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.geocoding.GeoNames;
import io.github.vincenzofiore.rootology.geocoding.Place;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * The dialog that creates or edits a garden (#14), with a place found by name or the browser's position
 * (#30). Shared by the gardens overview, which adds them, and the garden's plots page, which edits it (#56).
 */
final class GardenDialog {

    /** Stores the garden and returns it as saved: the caller knows the space and the person. */
    interface Save {
        GardenSummary save(String name, String town, BigDecimal latitude, BigDecimal longitude, String countryCode);
    }

    private final GeoNames geoNames;
    private final Save save;

    GardenDialog(GeoNames geoNames, Save save) {
        this.geoNames = geoNames;
        this.save = save;
    }

    void open(GardenSummary existing, Consumer<GardenSummary> saved) {
        UI ui = UI.getCurrent();
        TextField name = new TextField(ui.getTranslation("garden.name"));
        TextField town = new TextField(ui.getTranslation("garden.town"));
        BigDecimalField latitude = new BigDecimalField(ui.getTranslation("garden.latitude"));
        BigDecimalField longitude = new BigDecimalField(ui.getTranslation("garden.longitude"));
        TextField country = new TextField(ui.getTranslation("garden.country"));
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
        String language = ui.getLocale() == null ? "en" : ui.getLocale().getLanguage();
        ComboBox<Place> place = new ComboBox<>(ui.getTranslation("garden.place"));
        place.setPlaceholder(ui.getTranslation("garden.place.hint"));
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
        Button here = new Button(ui.getTranslation("garden.position"), e -> ((Button) e.getSource()).getElement().executeJs("""
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
                }, error -> Notification.show(ui.getTranslation("garden.position.none", error))));

        Dialog dialog = new Dialog(ui.getTranslation(existing == null ? "garden.new" : "garden.edit"));
        dialog.add(new FormLayout(name, place, town, latitude, longitude, country), here,
                new Paragraph(ui.getTranslation("garden.location.note")
                        + (geoNames.enabled() ? " " + ui.getTranslation("garden.geonames") : "")));
        dialog.getFooter().add(new Button(ui.getTranslation("common.cancel"), e -> dialog.close()),
                new Button(ui.getTranslation("common.save"), e -> {
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
                GardenSummary garden = save.save(name.getValue(), town.getValue(), latitude.getValue(),
                        longitude.getValue(), countryCode);
                dialog.close();
                saved.accept(garden);
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage());
            }
        }));
        dialog.open();
    }

    static BigDecimal coarse(double degrees) {
        return BigDecimal.valueOf(degrees).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
