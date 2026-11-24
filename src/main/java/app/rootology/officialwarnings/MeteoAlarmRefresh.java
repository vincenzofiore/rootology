package app.rootology.officialwarnings;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls MeteoAlarm every few minutes (#136), so the warnings shown are never older than MeteoAlarm's own
 * requirement of a delay under 10 minutes. Off in tests ({@code rootology.meteoalarm.enabled=false}), so no test
 * calls MeteoAlarm.
 */
@Component
@ConditionalOnProperty(name = "rootology.meteoalarm.enabled", havingValue = "true", matchIfMissing = true)
class MeteoAlarmRefresh {

    /** The first read comes soon after startup, without holding it up, so the card is filled before long. */
    private static final long FIRST_READ_MS = 10_000L;

    private final OfficialWarnings warnings;

    MeteoAlarmRefresh(OfficialWarnings warnings) {
        this.warnings = warnings;
    }

    /** {@code fixedDelay}: a slow read pushes the next one, it never piles up a second one behind it. */
    @Scheduled(fixedDelayString = "${rootology.meteoalarm.refresh-ms}", initialDelay = FIRST_READ_MS)
    void everyFewMinutes() {
        warnings.refresh();
    }
}
