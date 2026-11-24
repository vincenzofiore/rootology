package app.rootology.garden;

import java.math.BigDecimal;

/**
 * A garden got coordinates it did not have: created with them, given them, or moved. Published after the
 * change, so the forecast of the new place can be fetched without making the save wait for it. elevationM is
 * the garden's altitude in metres, or null when not known (#135): the forecast is cached per altitude too.
 */
public record GardenLocated(long gardenId, BigDecimal latitude, BigDecimal longitude, Integer elevationM) {
}
