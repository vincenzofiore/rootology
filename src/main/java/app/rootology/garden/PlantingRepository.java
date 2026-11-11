package app.rootology.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PlantingRepository extends JpaRepository<Planting, Long> {

    List<Planting> findByPlotIdOrderByPlantedOnDesc(Long plotId);

    List<Planting> findByPlotGardenSpaceIdOrderByPlantedOnDesc(Long spaceId);
}
