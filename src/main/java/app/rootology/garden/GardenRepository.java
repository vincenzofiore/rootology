package app.rootology.garden;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface GardenRepository extends JpaRepository<Garden, Long> {

    List<Garden> findBySpaceIdOrderById(Long spaceId);

    /** Whether a space has a garden at all (#165): a system read, for the nightly signal. */
    boolean existsBySpaceId(Long spaceId);

    /** Italian gardens with coordinates and no region yet (#136): a system read, for the background lookup. */
    @Query("select g from Garden g where g.regionCode is null and g.latitude is not null"
            + " and g.longitude is not null and (g.countryCode is null or g.countryCode = 'IT') order by g.id")
    List<Garden> withoutRegion();
}
