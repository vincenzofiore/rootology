package io.github.vincenzofiore.rootology.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/** One finished pipeline run, as stored in the pipeline_run table (see db/migration). */
@Entity
@Table(name = "pipeline_run")
public class PipelineRunRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String processId;
    private String questionId;
    private String question;

    private double noiseDropout;
    private int noiseSpurious;
    private double noiseMislabel;
    private boolean noiseNearSubject;
    private long noiseSeed;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> decisionAnswer;
    private double decisionConfidence;
    private boolean decisionEscalated;
    private String decisionReason;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> answer;
    private boolean answeredByHuman;
    private String reviewer;
    private String answerText;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> jenaAnswer;
    private Boolean matchesJena;

    @Column(nullable = false)
    private Instant finishedAt;

    protected PipelineRunRecord() {
    }

    PipelineRunRecord(String processId, String questionId, String question,
                      double noiseDropout, int noiseSpurious, double noiseMislabel, boolean noiseNearSubject,
                      long noiseSeed, List<String> decisionAnswer, double decisionConfidence,
                      boolean decisionEscalated, String decisionReason, List<String> answer,
                      boolean answeredByHuman, String reviewer, String answerText,
                      List<String> jenaAnswer, Boolean matchesJena, Instant finishedAt) {
        this.processId = processId;
        this.questionId = questionId;
        this.question = question;
        this.noiseDropout = noiseDropout;
        this.noiseSpurious = noiseSpurious;
        this.noiseMislabel = noiseMislabel;
        this.noiseNearSubject = noiseNearSubject;
        this.noiseSeed = noiseSeed;
        this.decisionAnswer = decisionAnswer;
        this.decisionConfidence = decisionConfidence;
        this.decisionEscalated = decisionEscalated;
        this.decisionReason = decisionReason;
        this.answer = answer;
        this.answeredByHuman = answeredByHuman;
        this.reviewer = reviewer;
        this.answerText = answerText;
        this.jenaAnswer = jenaAnswer;
        this.matchesJena = matchesJena;
        this.finishedAt = finishedAt;
    }

    public Long getId() { return id; }
    public String getProcessId() { return processId; }
    public String getQuestionId() { return questionId; }
    public String getQuestion() { return question; }
    public double getNoiseDropout() { return noiseDropout; }
    public int getNoiseSpurious() { return noiseSpurious; }
    public double getNoiseMislabel() { return noiseMislabel; }
    public boolean isNoiseNearSubject() { return noiseNearSubject; }
    public long getNoiseSeed() { return noiseSeed; }
    public List<String> getDecisionAnswer() { return decisionAnswer; }
    public double getDecisionConfidence() { return decisionConfidence; }
    public boolean isDecisionEscalated() { return decisionEscalated; }
    public String getDecisionReason() { return decisionReason; }
    public List<String> getAnswer() { return answer; }
    public boolean isAnsweredByHuman() { return answeredByHuman; }
    public String getReviewer() { return reviewer; }
    public String getAnswerText() { return answerText; }
    public List<String> getJenaAnswer() { return jenaAnswer; }
    public Boolean getMatchesJena() { return matchesJena; }
    public Instant getFinishedAt() { return finishedAt; }

    /** True when the run used degraded facts. */
    public boolean isNoisy() {
        return noiseDropout > 0 || noiseSpurious > 0 || noiseMislabel > 0;
    }
}
