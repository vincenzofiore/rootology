package io.github.vincenzofiore.rootology.garden;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** A site a space looks after, with a coarse location (town, coordinates rounded to about 1 km). */
@Entity
@Table(name = "garden")
class Garden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long spaceId;
    private String name;
    private String town;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String countryCode;
    /** Metres above sea level from GeoNames GTOPO30 (#62); null when not known. */
    @jakarta.persistence.Column(name = "elevation_m")
    private Integer elevationM;
    private Instant createdAt;

    protected Garden() {
    }

    Garden(long spaceId, String name, String town, BigDecimal latitude, BigDecimal longitude, String countryCode,
           Integer elevationM, Instant createdAt) {
        this.spaceId = spaceId;
        this.createdAt = createdAt;
        update(name, town, latitude, longitude, countryCode, elevationM);
    }

    void update(String name, String town, BigDecimal latitude, BigDecimal longitude, String countryCode,
                Integer elevationM) {
        this.name = name;
        this.town = town;
        this.latitude = latitude;
        this.longitude = longitude;
        this.countryCode = countryCode;
        this.elevationM = elevationM;
    }

    Long getId() { return id; }
    Long getSpaceId() { return spaceId; }
    String getName() { return name; }
    String getTown() { return town; }
    BigDecimal getLatitude() { return latitude; }
    BigDecimal getLongitude() { return longitude; }
    String getCountryCode() { return countryCode; }
    Integer getElevationM() { return elevationM; }
}
