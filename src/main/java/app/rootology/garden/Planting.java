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

import java.time.LocalDate;

/** A crop sown or transplanted in a plot; ended once harvested or removed. */
@Entity
@Table(name = "planting")
class Planting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id")
    private Plot plot;

    private String cropId;

    @Enumerated(EnumType.STRING)
    private PlantingMethod method;

    private LocalDate plantedOn;
    private LocalDate endedOn;
    private String notes;

    protected Planting() {
    }

    Planting(Plot plot, String cropId, PlantingMethod method, LocalDate plantedOn, String notes) {
        this.plot = plot;
        this.cropId = cropId;
        this.method = method;
        this.plantedOn = plantedOn;
        this.notes = notes;
    }

    void end(LocalDate endedOn) {
        if (endedOn.isBefore(plantedOn)) {
            throw new IllegalArgumentException("A planting cannot end before it started");
        }
        this.endedOn = endedOn;
    }

    boolean isGrowingOn(LocalDate day) {
        return !plantedOn.isAfter(day) && (endedOn == null || endedOn.isAfter(day));
    }

    Long getId() { return id; }
    Plot getPlot() { return plot; }
    String getCropId() { return cropId; }
    PlantingMethod getMethod() { return method; }
    LocalDate getPlantedOn() { return plantedOn; }
    LocalDate getEndedOn() { return endedOn; }
    String getNotes() { return notes; }
}
