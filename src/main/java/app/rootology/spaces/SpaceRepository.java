package app.rootology.spaces;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpaceRepository extends JpaRepository<Space, Long> {

    java.util.Optional<Space> findByPersonalOf(AppUser user);
}
