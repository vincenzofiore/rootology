package app.rootology.spaces;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLogin(String login);

    List<AppUser> findByLoginIn(Collection<String> logins);

    /** Whether another person shows this name (#106): their chosen name, or their login when they chose none. */
    @Query("""
            select count(u) > 0 from AppUser u where u.login <> :login
                and (lower(u.displayName) = lower(:name) or (u.displayName is null and u.login = lower(:name)))""")
    boolean nameShownByAnother(@Param("login") String login, @Param("name") String name);
}
