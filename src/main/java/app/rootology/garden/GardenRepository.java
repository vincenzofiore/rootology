package app.rootology.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface GardenRepository extends JpaRepository<Garden, Long> {

    List<Garden> findBySpaceIdOrderById(Long spaceId);

    /** Whether a space has a garden at all (#165): a system read, for the nightly signal. */
    boolean existsBySpaceId(Long spaceId);
}
