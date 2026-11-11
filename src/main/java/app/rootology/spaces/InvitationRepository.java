package app.rootology.spaces;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    List<Invitation> findBySpaceIdOrderByCreatedAtDesc(Long spaceId);

    long countByCreatedByAndCreatedAtAfter(String createdBy, java.time.Instant since);

    /** Privacy (#25): the invited address goes once the invitation can no longer be accepted. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Invitation i set i.invitedEmail = null "
            + "where i.invitedEmail is not null and i.expiresAt < :now")
    int forgetEmailsOfExpired(java.time.Instant now);
}
