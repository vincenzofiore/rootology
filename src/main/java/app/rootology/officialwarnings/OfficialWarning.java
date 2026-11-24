package app.rootology.officialwarnings;

import java.time.Instant;

/**
 * One active official warning of MeteoAlarm (#136): the region ({@code EMMA_ID}, the only geography the feed
 * gives), the level and the type of the phenomenon, the English fields of the feed, and the Italian text of the
 * warning's CAP document, which is null until that document has been read.
 *
 * @param identifier the warning's CAP identifier, the same across two feed reads
 * @param zone       the EMMA_ID of the region, for example {@code IT001}
 * @param areaDesc   the region's name as the feed writes it, for example {@code Calabria}
 * @param level      the awareness level, 1 to 4 (green, yellow, orange, red); 0 when the CAP document has none
 * @param type       the awareness type, 10 for rain; 0 when the CAP document has none
 * @param event      the event in English, for example {@code Yellow Rain Warning}
 * @param severity   CAP's severity, for example {@code Moderate}
 * @param sent       when the issuing service sent the warning
 * @param onset      when the warned phenomenon begins
 * @param expires    when the warning ends
 * @param capUrl     the CAP document of the warning
 * @param senderName the issuing service, in Italian once the CAP document is read
 * @param headline   the warning's headline, in Italian once the CAP document is read
 * @param description the warning's description, in Italian once the CAP document is read
 * @param instruction what to do, in Italian once the CAP document is read
 */
public record OfficialWarning(String identifier, String zone, String areaDesc, int level, int type, String event,
                              String severity, Instant sent, Instant onset, Instant expires, String capUrl,
                              String senderName, String headline, String description, String instruction) {
}
