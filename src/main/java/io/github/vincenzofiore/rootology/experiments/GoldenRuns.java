package io.github.vincenzofiore.rootology.experiments;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The quality suite as the QA view shows it (#20): the golden questions and the latest recorded live run
 * (src/main/resources/quality/live-runs/golden-*.json, written by GoldenLiveRecordingTest).
 */
@Component
public class GoldenRuns {

    static final String RUNS = "classpath:quality/live-runs/golden-*.json";

    /** One question's outcome in a live run; answer holds the proposed values when it escalated. */
    public record LiveResult(String id, String verdict, boolean intentMatches, String understoodIntent,
                             List<String> answer, String escalationReason, double confidence) {
    }

    /** A recorded live run: when, with which models, the count of each verdict, and the results by id. */
    public record LiveRun(String file, String startedAt, String llm, String decisionLayer,
                          Map<String, Integer> verdicts, long intentsUnderstood, Map<String, LiveResult> results) {
    }

    private final GoldenQuestions golden = GoldenQuestions.load();

    public GoldenQuestions golden() {
        return golden;
    }

    /** The most recent live run, by file name (they carry their date), if any was recorded. */
    public Optional<LiveRun> latest() {
        try {
            Resource[] runs = new PathMatchingResourcePatternResolver().getResources(RUNS);
            return Arrays.stream(runs).max(Comparator.comparing(Resource::getFilename)).map(GoldenRuns::read);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static LiveRun read(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = JsonMapper.builder().build().readTree(in);
            Map<String, Integer> verdicts = new LinkedHashMap<>();
            root.path("summary").path("verdicts").properties()
                    .forEach(e -> verdicts.put(e.getKey(), e.getValue().asInt()));
            Map<String, LiveResult> results = new LinkedHashMap<>();
            for (JsonNode r : root.path("results")) {
                results.put(r.path("id").asString(), new LiveResult(r.path("id").asString(),
                        r.path("verdict").isNull() ? "ERROR" : r.path("verdict").asString(),
                        r.path("intentMatches").asBoolean(), r.path("understoodIntent").asString(""),
                        r.path("answer").valueStream().map(JsonNode::asString).toList(),
                        r.path("escalationReason").isNull() ? null : r.path("escalationReason").asString(),
                        r.path("confidence").asDouble()));
            }
            return new LiveRun(resource.getFilename(), root.path("startedAt").asString(), root.path("llm").asString(),
                    root.path("decisionLayer").asString(), verdicts, root.path("summary").path("intentsUnderstood").asLong(),
                    results);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
