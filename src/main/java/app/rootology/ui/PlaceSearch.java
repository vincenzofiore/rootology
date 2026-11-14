package app.rootology.ui;

import com.vaadin.flow.component.combobox.ComboBox;
import app.rootology.geocoding.GeoNames;
import app.rootology.geocoding.Place;

/**
 * The place search behind every field that names a place (#30, #107): a combo box that asks GeoNames as the
 * person types, labelling each result with its region and, only abroad, its country. Shared by the garden
 * dialog and by Ask's "another place", so both search the same way.
 */
final class PlaceSearch {

    static ComboBox<Place> field(GeoNames geoNames, String label, String placeholder, String language) {
        ComboBox<Place> place = new ComboBox<>(label);
        place.setPlaceholder(placeholder);
        place.setItemLabelGenerator(Place::label);
        place.setItems(query -> geoNames.search(query.getFilter().orElse(""), language).stream()
                .skip(query.getOffset()).limit(query.getLimit()));
        return place;
    }

    private PlaceSearch() {
    }
}
