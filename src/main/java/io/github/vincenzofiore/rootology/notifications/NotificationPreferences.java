package io.github.vincenzofiore.rootology.notifications;

import java.time.LocalTime;

/**
 * Where to email and whether (waiting questions, weather alerts #37), and quiet hours in Italian time (both
 * null: none; from after to: overnight).
 */
public record NotificationPreferences(String email, boolean emailEscalations, boolean emailWeather, LocalTime quietFrom,
                                      LocalTime quietTo) {

    static final NotificationPreferences DEFAULT = new NotificationPreferences(null, true, true, null, null);

    /** True if this time falls in the quiet hours. */
    public boolean quietAt(LocalTime time) {
        if (quietFrom == null || quietTo == null || quietFrom.equals(quietTo)) {
            return false;
        }
        return quietFrom.isBefore(quietTo)
                ? !time.isBefore(quietFrom) && time.isBefore(quietTo)
                : !time.isBefore(quietFrom) || time.isBefore(quietTo);
    }
}
