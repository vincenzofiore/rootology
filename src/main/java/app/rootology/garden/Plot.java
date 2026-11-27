package app.rootology.garden;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A plot of a garden: a bed, a raised bed or a container, with its soil and size. Orientation, direct sun,
 * wind and floor are declared by the gardener, all optional (#54).
 */
@Entity
@Table(name = "plot")
class Plot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garden_id")
    private Garden garden;

    private String name;

    @Enumerated(EnumType.STRING)
    private PlotKind kind;

    @Enumerated(EnumType.STRING)
    private Soil soil;

    private BigDecimal areaM2;

    @Enumerated(EnumType.STRING)
    private Orientation orientation;

    @Enumerated(EnumType.STRING)
    private DirectSun directSun;

    @Enumerated(EnumType.STRING)
    private Wind wind;

    private Integer floor;

    /** Soil depth of a container area, in whole centimetres, declared by the gardener (#186). */
    @jakarta.persistence.Column(name = "depth_cm")
    private Integer depthCm;

    /** Hours of direct sun in summer (April-September) and in winter (October-March), declared (#89). */
    @jakarta.persistence.Column(name = "sun_hours_summer")
    private Integer sunHoursSummer;
    @jakarta.persistence.Column(name = "sun_hours_winter")
    private Integer sunHoursWinter;
    /** Who last declared the area's sun, its level or its hours, and when (#89). */
    @jakarta.persistence.Column(name = "sun_declared_by")
    private String sunDeclaredBy;
    @jakarta.persistence.Column(name = "sun_declared_at")
    private java.time.Instant sunDeclaredAt;

    protected Plot() {
    }

    Plot(Garden garden, String name, PlotKind kind, Soil soil, BigDecimal areaM2) {
        this.garden = garden;
        update(name, kind, soil, areaM2, null, null, null, null);
    }

    void update(String name, PlotKind kind, Soil soil, BigDecimal areaM2, Orientation orientation,
                DirectSun directSun, Wind wind, Integer floor) {
        this.name = name;
        this.kind = kind;
        this.soil = soil;
        this.areaM2 = areaM2;
        this.orientation = orientation;
        this.directSun = directSun;
        this.wind = wind;
        this.floor = floor;
    }

    Long getId() { return id; }
    Garden getGarden() { return garden; }
    String getName() { return name; }
    PlotKind getKind() { return kind; }
    Soil getSoil() { return soil; }
    BigDecimal getAreaM2() { return areaM2; }
    Orientation getOrientation() { return orientation; }
    DirectSun getDirectSun() { return directSun; }
    Wind getWind() { return wind; }
    Integer getFloor() { return floor; }
    Integer getDepthCm() { return depthCm; }
    Integer getSunHoursSummer() { return sunHoursSummer; }
    Integer getSunHoursWinter() { return sunHoursWinter; }
    String getSunDeclaredBy() { return sunDeclaredBy; }
    java.time.Instant getSunDeclaredAt() { return sunDeclaredAt; }

    /**
     * The area's sun hours (#89); who declared and when change only when the level or the hours change, so
     * saving the dialog for another reason keeps the earlier declaration.
     */
    void declareSun(Integer summer, Integer winter, String by, java.time.Instant at) {
        boolean changed = !java.util.Objects.equals(summer, sunHoursSummer)
                || !java.util.Objects.equals(winter, sunHoursWinter);
        this.sunHoursSummer = summer;
        this.sunHoursWinter = winter;
        if (changed) {
            this.sunDeclaredBy = by;
            this.sunDeclaredAt = at;
        }
    }

    /**
     * The soil depth of a container area (#186), declared with the area; null in open ground and under a
     * greenhouse, where a depth means nothing, so the dialog's field is hidden and the value cleared there.
     */
    void declareDepth(Integer depthCm) {
        this.depthCm = depthCm;
    }

    /** The declared level changed (#89): it is a declaration of the area's sun too. */
    void sunLevelDeclared(String by, java.time.Instant at) {
        this.sunDeclaredBy = by;
        this.sunDeclaredAt = at;
    }
}
