package app.rootology.alerts;

import app.rootology.garden.GardenViews.GrowingGarden;
import app.rootology.notifications.NotificationService;
import app.rootology.officialwarnings.OfficialWarning;
import app.rootology.officialwarnings.OfficialWarnings;
import app.rootology.officialwarnings.WarningZones;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The one line about the official warnings of the garden's region in the morning alert (#168), at most one a
 * day: never a warning's own text, which the card on the garden home shows as it is with its issuer, time of
 * issue, link and disclaimer. No known zone, a feed that may be old or no active warning means no line.
 */
@Component
class OfficialWarningAlert {

    private final OfficialWarnings official;
    private final NotificationService notifications;

    OfficialWarningAlert(OfficialWarnings official, NotificationService notifications) {
        this.official = official;
        this.notifications = notifications;
    }

    /** One line to every member of the garden's space; 1 when it reached someone new. */
    int check(GrowingGarden garden, LocalDate today) {
        Optional<String> zone = WarningZones.emmaId(garden.garden().countryCode(), garden.garden().regionCode());
        if (zone.isEmpty() || official.stale()) {
            return 0;
        }
        List<OfficialWarning> active = official.active(zone.get());
        if (active.isEmpty()) {
            return 0;
        }
        int notified = notifications.weatherAlert(garden.spaceId(), garden.garden().id(),
                "official:" + garden.garden().id() + ":" + today,
                OfficialWarningLine.title(active), OfficialWarningLine.body(active));
        return notified > 0 ? 1 : 0;
    }
}
