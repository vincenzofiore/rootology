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

    /** Where the area sits on the garden's plan (#90): its top-left corner and size in metres, or all null. */
    @jakarta.persistence.Column(name = "plan_x_m")
    private BigDecimal planXM;
    @jakarta.persistence.Column(name = "plan_y_m")
    private BigDecimal planYM;
    @jakarta.persistence.Column(name = "plan_width_m")
    private BigDecimal planWidthM;
    @jakarta.persistence.Column(name = "plan_depth_m")
    private BigDecimal planDepthM;

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
    BigDecimal getPlanXM() { return planXM; }
    BigDecimal getPlanYM() { return planYM; }
    BigDecimal getPlanWidthM() { return planWidthM; }
    BigDecimal getPlanDepthM() { return planDepthM; }

    boolean isPlaced() {
        return planXM != null;
    }

    void place(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal depth) {
        this.planXM = x;
        this.planYM = y;
        this.planWidthM = width;
        this.planDepthM = depth;
    }

    void unplace() {
        place(null, null, null, null);
    }
}
