package app.rootology.geocoding;

/**
 * A populated place found by name: its region, ISO country code, coordinates (WGS84) and population,
 * which tells two places of the same name apart (#107).
 */
public record Place(String name, String region, String countryCode, String countryName, double latitude,
                    double longitude, long population) {

    /** A place whose population GeoNames did not give. */
    public Place(String name, String region, String countryCode, String countryName, double latitude,
                 double longitude) {
        this(name, region, countryCode, countryName, latitude, longitude, 0);
    }

    /**
     * How the place is shown in a list, for example "Bologna, Emilia-Romagna", or "Lyon, Auvergne-Rhône-Alpes,
     * France": the country only outside Italy, where most gardens are (#87).
     */
    public String label() {
        return name + (region == null || region.isBlank() || region.equals(name) ? "" : ", " + region)
                + (countryName == null || countryName.isBlank() || "IT".equals(countryCode) ? "" : ", " + countryName);
    }
}
