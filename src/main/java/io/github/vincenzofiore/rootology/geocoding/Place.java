package io.github.vincenzofiore.rootology.geocoding;

/** A populated place found by name: its region, ISO country code and coordinates (WGS84). */
public record Place(String name, String region, String countryCode, String countryName, double latitude,
                    double longitude) {

    /** How the place is shown in a list, for example "Bologna, Emilia-Romagna, Italy". */
    public String label() {
        return name + (region == null || region.isBlank() || region.equals(name) ? "" : ", " + region)
                + (countryName == null || countryName.isBlank() ? "" : ", " + countryName);
    }
}
