package app.rootology.privacy;

import app.rootology.spaces.SpaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A person's data as one JSON file (#40, docs/privacy.md): their account, spaces, the gardens of those spaces,
 * the questions they asked or answered, notifications, preferences, usage, proposals and roles. Other
 * people's questions in shared spaces are theirs, so they are not included.
 */
@Service
public class DataExport {

    private final PersonalData people;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    DataExport(PersonalData people, Clock clock) {
        this.people = people;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public byte[] export(String githubLogin) {
        String login = SpaceService.normalizeLogin(githubLogin);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", clock.instant().toString());
        data.put("githubLogin", login);
        data.putAll(people.spaces().export(login));
        data.putAll(people.gardens().export(people.spaces().spacesOf(login)));
        data.put("questions", people.runs().export(login));
        data.put("escalations", people.escalations().export(login));
        data.putAll(people.notifications().export(login));
        data.putAll(people.telegram().export(login));
        data.putAll(people.usage().export(login));
        data.put("knowledgeProposals", people.proposals().export(login));
        data.put("appRoles", people.roles().export(login));
        data.put("accessRequests", people.access().export(login));
        data.put("accountFlags", people.accountFlags().export(login));
        return json.writeValueAsBytes(plain(data));
    }

    /** JDBC values as JSON-friendly ones: instants and dates as ISO text, SQL arrays as lists. */
    private static Object plain(Object value) {
        return switch (value) {
            case null -> null;
            case Map<?, ?> map -> {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((k, v) -> out.put(String.valueOf(k), plain(v)));
                yield out;
            }
            case List<?> list -> list.stream().map(DataExport::plain).toList();
            case Timestamp t -> t.toInstant().toString();
            case java.sql.Date d -> d.toLocalDate().toString();
            case Time t -> t.toLocalTime().toString();
            case Array a -> {
                try {
                    yield Arrays.asList((Object[]) a.getArray());
                } catch (SQLException e) {
                    throw new IllegalStateException("Could not read an array for the export", e);
                }
            }
            default -> value;
        };
    }
}
