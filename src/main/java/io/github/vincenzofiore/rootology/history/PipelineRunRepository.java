package io.github.vincenzofiore.rootology.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineRunRepository extends JpaRepository<PipelineRunRecord, Long> {

    List<PipelineRunRecord> findTop100ByOrderByFinishedAtDesc();

    List<PipelineRunRecord> findByQuestionIdOrderByFinishedAtDesc(String questionId);
}
