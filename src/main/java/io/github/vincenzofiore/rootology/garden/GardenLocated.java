package io.github.vincenzofiore.rootology.garden;

import java.math.BigDecimal;

/**
 * A garden got coordinates it did not have: created with them, given them, or moved. Published after the
 * change, so the forecast of the new place can be fetched without making the save wait for it.
 */
public record GardenLocated(long gardenId, BigDecimal latitude, BigDecimal longitude) {
}
