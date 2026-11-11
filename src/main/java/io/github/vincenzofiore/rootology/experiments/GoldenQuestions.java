package io.github.vincenzofiore.rootology.experiments;

import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The golden questions of the quality suite (#20), from src/main/resources/quality/golden-questions.yaml:
 * read by the offline and live tests and shown in the QA view.
 */
public record GoldenQuestions(LocalDate today, List<Case> cases) {

    static final String RESOURCE = "/quality/golden-questions.yaml";

    /** A space fact to add before asking (#24): another name for a crop or organism. */
    public record SpaceAlias(String targetId, String name, String language) {
    }

    /**
     * answer is null when the question must escalate; escalate is then a part of the expected reason.
     * offDomain: the domain filter must refuse the question (#53), with no answer and no escalation.
     */
    public record Case(String id, String question, Intent intent, String garden, String weather, List<SpaceAlias> spaceFacts,
                List<String> answer, String escalate, List<String> notes, boolean offDomain) {
    }

    @SuppressWarnings("unchecked")
    public static GoldenQuestions load() {
        try (InputStream in = GoldenQuestions.class.getResourceAsStream(RESOURCE)) {
            Map<String, Object> root = new Yaml().load(in);
            Object today = root.get("today");
            LocalDate day = today instanceof Date d ? d.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
                    : LocalDate.parse(String.valueOf(today));
            List<Case> cases = ((List<Map<String, Object>>) root.get("cases")).stream().map(GoldenQuestions::parse)
                    .toList();
            return new GoldenQuestions(day, cases);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Case parse(Map<String, Object> c) {
        Map<String, Object> i = (Map<String, Object>) c.get("intent");
        Intent intent = new Intent(QueryKind.valueOf((String) i.get("kind")), (String) i.get("subject"),
                (String) i.get("season"), (String) i.get("language"), (String) i.get("zone"), (String) i.get("month"),
                (String) i.get("garden"));
        Map<String, Object> expect = (Map<String, Object>) c.get("expect");
        List<SpaceAlias> facts = ((List<Map<String, Object>>) c.getOrDefault("spaceFacts", List.of())).stream()
                .map(f -> new SpaceAlias((String) f.get("alias"), (String) f.get("name"), (String) f.get("language")))
                .toList();
        List<String> answer = expect.containsKey("answer")
                ? ((List<Object>) expect.get("answer")).stream().map(String::valueOf).toList() : null;
        return new Case((String) c.get("id"), (String) c.get("question"), intent,
                (String) c.getOrDefault("garden", "none"), (String) c.getOrDefault("weather", "missing"), facts,
                answer, (String) expect.get("escalate"), (List<String>) expect.getOrDefault("notes", List.of()),
                Boolean.TRUE.equals(expect.get("offDomain")));
    }
}
