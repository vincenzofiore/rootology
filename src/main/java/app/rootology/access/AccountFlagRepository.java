package app.rootology.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface AccountFlagRepository extends JpaRepository<AccountFlag, Long> {

    /** An account is flagged once: any flag it ever had, whatever its decision, keeps the signal away. */
    boolean existsByLogin(String login);

    Optional<AccountFlag> findByLoginAndStatus(String login, AccountFlagStatus status);

    List<AccountFlag> findByStatusOrderByFlaggedAtDesc(AccountFlagStatus status);

    List<AccountFlag> findByLoginOrderByIdDesc(String login);

    /** Retention (#40): decided flags only, since one waiting for an admin is still in use. */
    @Modifying
    @Query("delete from AccountFlag f where f.status <> :open and f.decidedAt < :before")
    int deleteDecidedBefore(@Param("open") AccountFlagStatus open, @Param("before") Instant before);

    /** Account deletion (#40): the flags of the person go with the account. */
    @Modifying
    @Query("delete from AccountFlag f where f.login = :login")
    int deleteByLogin(@Param("login") String login);

    /** Account deletion (#40): a decided flag names no one who no longer exists. */
    @Modifying
    @Query("update AccountFlag f set f.decidedBy = :replacement where f.decidedBy = :login")
    int renameDecider(@Param("login") String login, @Param("replacement") String replacement);
}
