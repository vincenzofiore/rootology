package app.rootology.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineRunRepository extends JpaRepository<PipelineRunRecord, Long> {

    List<PipelineRunRecord> findTop100BySpaceIdOrderByFinishedAtDesc(Long spaceId);

    /** The questions of one garden (#56), newest first. */
    List<PipelineRunRecord> findTop100BySpaceIdAndGardenIdOrderByFinishedAtDesc(Long spaceId, Long gardenId);

    /** The questions asked outside a garden, stored before #56 in a space with several gardens, or of a deleted garden. */
    List<PipelineRunRecord> findTop100BySpaceIdAndGardenIdIsNullOrderByFinishedAtDesc(Long spaceId);
}
