package io.github.vincenzofiore.rootology.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface GardenRepository extends JpaRepository<Garden, Long> {

    List<Garden> findBySpaceIdOrderById(Long spaceId);
}
