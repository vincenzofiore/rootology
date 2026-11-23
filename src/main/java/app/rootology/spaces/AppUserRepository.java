package app.rootology.spaces;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLogin(String login);

    List<AppUser> findByLoginIn(Collection<String> logins);

    /** True while an admin blocks the account (#165): one cheap query on every sign-in and navigation. */
    boolean existsByLoginAndBlockedAtIsNotNull(String login);

    /** Every blocked account, for the admin page (#165). */
    List<AppUser> findByBlockedAtIsNotNull();

    /** Unblocking (#165) clears all three columns at once; who unblocked and when stay in the log only. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AppUser u set u.blockedAt = null, u.blockedBy = null, u.blockedReason = null where u.id = :id")
    void clearBlock(@Param("id") Long id);

    /** Whether another person shows this name (#106): their chosen name, or their login when they chose none. */
    @Query("""
            select count(u) > 0 from AppUser u where u.login <> :login
                and (lower(u.displayName) = lower(:name) or (u.displayName is null and u.login = lower(:name)))""")
    boolean nameShownByAnother(@Param("login") String login, @Param("name") String name);
}
