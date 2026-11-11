package io.github.vincenzofiore.rootology.access;

import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    Optional<AccessRequest> findFirstByProviderAndSubjectOrderByIdDesc(IdentityProvider provider, String subject);

    Optional<AccessRequest> findFirstByProviderAndSubjectAndStatusOrderByIdDesc(IdentityProvider provider,
                                                                              String subject,
                                                                              AccessRequestStatus status);

    boolean existsByProviderAndSubjectAndStatus(IdentityProvider provider, String subject, AccessRequestStatus status);

    List<AccessRequest> findByStatusOrderByCreatedAtAsc(AccessRequestStatus status);

    List<AccessRequest> findTop30ByStatusNotOrderByDecidedAtDesc(AccessRequestStatus status);

    long countByCreatedAtGreaterThanEqual(Instant since);

    List<AccessRequest> findByProviderAndSubject(IdentityProvider provider, String subject);

    @Modifying
    @Query("delete from AccessRequest r where r.provider = :provider and r.subject = :subject and r.status = :status")
    int deleteByProviderAndSubjectAndStatus(@Param("provider") IdentityProvider provider,
                                            @Param("subject") String subject,
                                            @Param("status") AccessRequestStatus status);

    @Modifying
    @Query("delete from AccessRequest r where r.status = :status and r.decidedAt < :before")
    int deleteDecidedBefore(@Param("status") AccessRequestStatus status, @Param("before") Instant before);

    @Modifying
    @Query("delete from AccessRequest r where r.status = :status and r.createdAt < :before")
    int deleteNeverDecidedBefore(@Param("status") AccessRequestStatus status, @Param("before") Instant before);
}
