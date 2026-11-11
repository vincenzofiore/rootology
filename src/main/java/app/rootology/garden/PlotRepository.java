package app.rootology.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PlotRepository extends JpaRepository<Plot, Long> {

    List<Plot> findByGardenIdOrderByName(Long gardenId);
}
