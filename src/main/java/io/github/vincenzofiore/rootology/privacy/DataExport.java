package io.github.vincenzofiore.rootology.privacy;

import io.github.vincenzofiore.rootology.enrichment.ProposalPersonalData;
import io.github.vincenzofiore.rootology.garden.GardenPersonalData;
import io.github.vincenzofiore.rootology.history.RunPersonalData;
import io.github.vincenzofiore.rootology.notifications.NotificationPersonalData;
import io.github.vincenzofiore.rootology.notifications.TelegramPersonalData;
import io.github.vincenzofiore.rootology.pipeline.EscalationPersonalData;
import io.github.vincenzofiore.rootology.roles.RolePersonalData;
import io.github.vincenzofiore.rootology.spaces.SpacePersonalData;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.usage.UsagePersonalData;
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

    private final SpacePersonalData spaces;
    private final GardenPersonalData gardens;
    private final RunPersonalData runs;
    private final EscalationPersonalData escalations;
    private final NotificationPersonalData notifications;
    private final TelegramPersonalData telegram;
    private final UsagePersonalData usage;
    private final ProposalPersonalData proposals;
    private final RolePersonalData roles;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    DataExport(SpacePersonalData spaces, GardenPersonalData gardens, RunPersonalData runs,
               EscalationPersonalData escalations, NotificationPersonalData notifications, TelegramPersonalData telegram,
               UsagePersonalData usage,
               ProposalPersonalData proposals, RolePersonalData roles, Clock clock) {
        this.spaces = spaces;
        this.gardens = gardens;
        this.runs = runs;
        this.escalations = escalations;
        this.notifications = notifications;
        this.telegram = telegram;
        this.usage = usage;
        this.proposals = proposals;
        this.roles = roles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public byte[] export(String githubLogin) {
        String login = SpaceService.normalizeLogin(githubLogin);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", clock.instant().toString());
        data.put("githubLogin", login);
        data.putAll(spaces.export(login));
        data.putAll(gardens.export(spaces.spacesOf(login)));
        data.put("questions", runs.export(login));
        data.put("escalations", escalations.export(login));
        data.putAll(notifications.export(login));
        data.putAll(telegram.export(login));
        data.putAll(usage.export(login));
        data.put("knowledgeProposals", proposals.export(login));
        data.put("appRoles", roles.export(login));
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
