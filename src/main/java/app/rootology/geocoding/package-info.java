/**
 * Garden coordinates from a place name or a position (#30), through the GeoNames web services
 * (CC BY 4.0, commercial use allowed, attribution to GeoNames; a free account, `ROOTOLOGY_GEONAMES_USERNAME`).
 * Never blocks saving a garden: results are cached, failures open a circuit breaker, and without the
 * service the person can still use the browser's position or type the coordinates.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Geocoding",
        allowedDependencies = { "resilience" }
)
package app.rootology.geocoding;
