package app.rootology.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PipelineRunRepository extends JpaRepository<PipelineRunRecord, Long> {

    List<PipelineRunRecord> findTop100BySpaceIdOrderByFinishedAtDesc(Long spaceId);

    /** The questions of one garden (#56), newest first. */
    List<PipelineRunRecord> findTop100BySpaceIdAndGardenIdOrderByFinishedAtDesc(Long spaceId, Long gardenId);

    /** The questions asked outside a garden, stored before #56 in a space with several gardens, or of a deleted garden. */
    List<PipelineRunRecord> findTop100BySpaceIdAndGardenIdIsNullOrderByFinishedAtDesc(Long spaceId);

    /**
     * The last questions one person asked that the domain filter judged (#165): the runs where it could not
     * judge (#161) and the ones answered from a waiting question are left out. For the nightly signal only.
     */
    @Query("""
            select r from PipelineRunRecord r
            where r.startedBy = :login and r.domainUnjudged = false and r.answeredByHuman = false
            order by r.finishedAt desc""")
    List<PipelineRunRecord> judgedQuestions(@Param("login") String login,
                                            org.springframework.data.domain.Pageable newest);
}
