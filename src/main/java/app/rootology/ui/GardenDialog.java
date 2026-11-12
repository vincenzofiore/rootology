package app.rootology.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.geocoding.GeoNames;
import app.rootology.facts.ClimateCells;
import app.rootology.geocoding.Place;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * The dialog that creates or edits a garden (#14), with a place found by name or the browser's position
 * (#30). Shared by the gardens overview, which adds them, and the garden's plots page, which edits it (#56).
 */
final class GardenDialog {

    /** Stores the garden and returns it as saved: the caller knows the space and the person. */
    interface Save {
        GardenSummary save(String name, String town, BigDecimal latitude, BigDecimal longitude, String countryCode,
                           Integer elevationM);
    }

    private final GeoNames geoNames;
    private final ClimateCells cells;
    private final Save save;

    GardenDialog(GeoNames geoNames, ClimateCells cells, Save save) {
        this.geoNames = geoNames;
        this.cells = cells;
        this.save = save;
    }

    void open(GardenSummary existing, Consumer<GardenSummary> saved) {
        open(existing, saved, null, null);
    }

    /**
     * The same dialog, opened from a point chosen on the map (#66): the coordinates arrive rounded like every
     * other position, and the town and its country come from the nearest place, as with "Use my position".
     */
    void open(GardenSummary existing, Consumer<GardenSummary> saved, double latitude, double longitude) {
        open(existing, saved, Double.valueOf(latitude), Double.valueOf(longitude));
    }

    private void open(GardenSummary existing, Consumer<GardenSummary> saved, Double prefilledLatitude,
                      Double prefilledLongitude) {
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
        boolean fromMap = prefilledLatitude != null && prefilledLongitude != null;
        // Only coordinates typed by hand are questioned (#66): a point on the map, a place found by name or the
        // browser's position are real places. Typing in either field makes the position the person's own again.
        boolean[] typed = {false};
        latitude.addValueChangeListener(e -> typed[0] |= e.isFromClient());
        longitude.addValueChangeListener(e -> typed[0] |= e.isFromClient());
        if (fromMap) {
            latitude.setValue(coarse(prefilledLatitude));
            longitude.setValue(coarse(prefilledLongitude));
            geoNames.nearestPlace(prefilledLatitude, prefilledLongitude, language).ifPresent(p -> {
                town.setValue(p.name());
                country.setValue(p.countryCode() == null ? "" : p.countryCode());
            });
        }
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
                typed[0] = false;
            }
        });
        // From the map the place is already chosen: a second search here would only compete with it (#66).
        place.setVisible(geoNames.enabled() && !fromMap);

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
                    typed[0] = false;
                    // The nearest town and its country, as when a place is chosen from the search.
                    geoNames.nearestPlace(lat, lon, language).ifPresent(p -> {
                        town.setValue(p.name());
                        country.setValue(p.countryCode());
                    });
                }, error -> Notification.show(ui.getTranslation("garden.position.none", error))));

        here.setVisible(!fromMap);

        Dialog dialog = new Dialog(ui.getTranslation(existing == null ? "garden.new" : "garden.edit"));
        if (fromMap) {
            dialog.add(new Paragraph(ui.getTranslation("garden.frommap")));
            name.focus();
        }
        dialog.add(new FormLayout(name, place, town, latitude, longitude, country), here,
                new Paragraph(ui.getTranslation("garden.location.note")
                        + (geoNames.enabled() ? " " + ui.getTranslation("garden.geonames") : "")));
        // Typed coordinates in open sea are only questioned, never refused (#66): a second Save keeps them.
        Paragraph seaWarning = new Paragraph(ui.getTranslation("garden.sea"));
        seaWarning.addClassName("rootology-warning");
        seaWarning.setVisible(false);
        dialog.add(seaWarning);
        BigDecimal[] warnedAt = new BigDecimal[2];
        Button saveButton = new Button(ui.getTranslation("common.save"));
        dialog.getFooter().add(new Button(ui.getTranslation("common.cancel"), e -> dialog.close()), saveButton);
        saveButton.addClickListener(e -> {
            BigDecimal lat = latitude.getValue();
            BigDecimal lon = longitude.getValue();
            boolean warned = lat != null && lon != null && lat.equals(warnedAt[0]) && lon.equals(warnedAt[1]);
            if (typed[0] && lat != null && lon != null && !warned && cells != null
                    && cells.looksLikeSea(lat.doubleValue(), lon.doubleValue())) {
                warnedAt[0] = lat;
                warnedAt[1] = lon;
                seaWarning.setVisible(true);
                saveButton.setText(ui.getTranslation("garden.sea.save"));
                return;
            }
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
                        longitude.getValue(), countryCode, elevation(existing, latitude.getValue(), longitude.getValue()));
                dialog.close();
                saved.accept(garden);
            } catch (RuntimeException ex) {
                UiErrors.show(ui, ex);
            }
        });
        dialog.open();
    }

    /**
     * The elevation for the profile (#62): the old one when the place did not change, otherwise GTOPO30's
     * for the new place. A failed lookup leaves it null and never blocks saving.
     */
    private Integer elevation(GardenSummary existing, BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        boolean same = existing != null && existing.latitude() != null && existing.longitude() != null
                && latitude.compareTo(existing.latitude()) == 0 && longitude.compareTo(existing.longitude()) == 0;
        if (same && existing.elevationM() != null) {
            return existing.elevationM();
        }
        return geoNames.elevation(latitude.doubleValue(), longitude.doubleValue()).orElse(null);
    }

    static BigDecimal coarse(double degrees) {
        return BigDecimal.valueOf(degrees).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
