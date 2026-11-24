package app.rootology.officialwarnings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The active official warnings of Italy (#136), kept in memory and never in the database: they are read from
 * MeteoAlarm's feed, which is polled every few minutes because MeteoAlarm states a delay never over 10 minutes.
 * A refresh reads the CAP document only of a warning it has not seen (a reused one keeps the Italian text it
 * already has), drops the warnings gone from the feed and the expired ones, and remembers when the last read
 * succeeded, so the app can say how old what it shows is and stop showing it at all once it is too old.
 * <p>
 * A failing read costs a log line and keeps the last list. Reads are for the app's screens: they never fetch.
 */
@Service
public class OfficialWarnings {

    /** After this long without a successful read the list is no longer shown as current. */
    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(OfficialWarnings.class);

    private final MeteoAlarmClient client;
    private final Clock clock;

    /** Replaced as a whole on every refresh, so a reader always sees a whole list. */
    private volatile Map<String, OfficialWarning> warnings = Map.of();
    private volatile Instant lastRead;

    @Autowired
    OfficialWarnings(MeteoAlarmClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    /**
     * Reads the feed once and updates the warnings. A read that fails leaves the last list as it was; a CAP
     * document that cannot be read leaves its warning without the Italian text, which the next refresh retries.
     */
    public void refresh() {
        List<OfficialWarning> read;
        try {
            read = MeteoAlarmFeed.parseFeed(client.feed());
        } catch (IOException | RuntimeException e) {
            log.warn("MeteoAlarm feed not read, keeping the last warnings: {}", e.getClass().getSimpleName());
            return;
        }
        Instant now = clock.instant();
        Map<String, OfficialWarning> next = new LinkedHashMap<>();
        for (OfficialWarning warning : read) {
            if (warning.expires() != null && !warning.expires().isAfter(now)) {
                continue;
            }
            next.put(warning.identifier(), complete(warning));
        }
        warnings = Map.copyOf(next);
        lastRead = now;
    }

    /** The warnings of one region ({@code EMMA_ID}) still in force, the gravest first, then the earliest onset. */
    public List<OfficialWarning> active(String zone) {
        if (zone == null || zone.isBlank()) {
            return List.of();
        }
        Instant now = clock.instant();
        return warnings.values().stream()
                .filter(warning -> zone.equals(warning.zone()))
                .filter(warning -> warning.expires() == null || warning.expires().isAfter(now))
                .sorted(Comparator.comparingInt(OfficialWarning::level).reversed()
                        .thenComparing(OfficialWarning::onset, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** When the feed was last read successfully; empty when it never was. */
    public Optional<Instant> lastRead() {
        return Optional.ofNullable(lastRead);
    }

    /** True when no read succeeded yet, or the last one is older than ten minutes: what is kept is not current. */
    public boolean stale() {
        Instant read = lastRead;
        return read == null || !read.plus(STALE_AFTER).isAfter(clock.instant());
    }

    /** The warning with its Italian text, read now if this one has never been read. */
    private OfficialWarning complete(OfficialWarning warning) {
        OfficialWarning known = warnings.get(warning.identifier());
        if (known != null && known.headline() != null) {
            return MeteoAlarmFeed.withText(warning, MeteoAlarmFeed.textOf(known));
        }
        try {
            return MeteoAlarmFeed.withText(warning, MeteoAlarmFeed.parseCap(client.cap(warning.capUrl())));
        } catch (IOException | RuntimeException e) {
            log.warn("Italian text of a MeteoAlarm warning not read: {}", e.getClass().getSimpleName());
            return warning;
        }
    }
}
