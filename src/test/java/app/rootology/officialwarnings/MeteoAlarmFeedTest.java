package app.rootology.officialwarnings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The parser against the recorded MeteoAlarm documents: fields, the Italian info, unreadable input. */
class MeteoAlarmFeedTest {

    private static final byte[] FEED = fixture("feed-italy.xml");
    private static final byte[] CAP = fixture("cap-calabria-rain.xml");

    @Test
    void readsTheEntriesOfTheFeed() throws IOException {
        List<OfficialWarning> warnings = MeteoAlarmFeed.parseFeed(FEED);

        assertThat(warnings).extracting(OfficialWarning::zone)
                .containsExactly("IT001", "IT019", "IT001", "IT001");
        OfficialWarning calabria = warnings.getFirst();
        assertThat(calabria.identifier()).isEqualTo("2.49.0.0.380.3.IT.261123103426.074");
        assertThat(calabria.areaDesc()).isEqualTo("Calabria");
        assertThat(calabria.event()).isEqualTo("Yellow Rain Warning");
        assertThat(calabria.severity()).isEqualTo("Moderate");
        assertThat(calabria.sent()).isEqualTo(Instant.parse("2026-11-23T09:34:28Z"));
        assertThat(calabria.onset()).isEqualTo(Instant.parse("2026-11-25T00:00:00Z"));
        assertThat(calabria.expires()).isEqualTo(Instant.parse("2026-11-25T22:59:00Z"));
        assertThat(calabria.capUrl()).isEqualTo(
                "https://feeds.meteoalarm.org/api/v1/warnings/feeds-italy/3195fb5e-9ccb-4582-8d0d-2fcd4c3a2d79");
        // The Italian text and the level come from the CAP document, which is read only later.
        assertThat(calabria.headline()).isNull();
        assertThat(calabria.level()).isZero();
    }

    @Test
    void readsTheItalianInfoOfTheCapDocument() throws IOException {
        MeteoAlarmFeed.CapText text = MeteoAlarmFeed.parseCap(CAP);

        assertThat(text.level()).isEqualTo(2);
        assertThat(text.type()).isEqualTo(10);
        assertThat(text.senderName()).isEqualTo("Servizio Meteorologico dell'Aeronautica Militare");
        assertThat(text.headline()).isEqualTo("Allerta Pioggia Giallo per l'Italia - Calabria");
        assertThat(text.description()).startsWith("Sono previsti fenomeni meteorologici di moderata intensità");
        assertThat(text.instruction()).startsWith("STARE (RIMANERE) ATTENTI");
    }

    @Test
    void anEntryWithoutAZoneIsSkippedTheOthersAreRead() throws IOException {
        byte[] feed = feed("""
                  <entry>
                    <cap:areaDesc>Calabria</cap:areaDesc>
                    <cap:event>Yellow Rain Warning</cap:event>
                    <cap:sent>2026-11-23T09:34:28+00:00</cap:sent>
                    <cap:onset>2026-11-25T00:00:00+00:00</cap:onset>
                    <cap:expires>2026-11-25T22:59:00+00:00</cap:expires>
                    <cap:identifier>2.49.0.0.380.3.IT.261123103426.999</cap:identifier>
                    <link type="application/cap+xml" href="https://feeds.meteoalarm.org/api/v1/warnings/x"/>
                  </entry>
                """, entry("IT019"));

        assertThat(MeteoAlarmFeed.parseFeed(feed)).extracting(OfficialWarning::zone).containsExactly("IT019");
    }

    @Test
    void anEntityInTheDescriptionComesOutDecoded() throws IOException {
        byte[] cap = """
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <info>
                    <language>it-IT</language>
                    <description>Sono previsti fenomeni meteorologici di moderata intensità &gt;800 METRI ALPI</description>
                  </info>
                </alert>
                """.getBytes(StandardCharsets.UTF_8);

        assertThat(MeteoAlarmFeed.parseCap(cap).description())
                .as("the area keeps its own character, never its entity (#171)")
                .endsWith(">800 METRI ALPI");
    }

    @Test
    void aDocumentWithADoctypeIsRefused() {
        byte[] doctype = """
                <?xml version="1.0" encoding="utf-8"?>
                <!DOCTYPE alert SYSTEM "http://example.invalid/cap.dtd">
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2"><info><language>it-IT</language></info></alert>
                """.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> MeteoAlarmFeed.parseCap(doctype)).isInstanceOf(IOException.class);
    }

    /** The fixture's header, up to its first entry, with entries built here after it. */
    private static final String HEAD = new String(FEED, StandardCharsets.UTF_8)
            .substring(0, new String(FEED, StandardCharsets.UTF_8).indexOf("<entry>"));

    private static byte[] feed(String... entries) {
        return (HEAD + String.join("", entries) + "</feed>").getBytes(StandardCharsets.UTF_8);
    }

    private static String entry(String zone) {
        return """
                  <entry>
                    <cap:geocode><valueName>EMMA_ID</valueName><value>%s</value></cap:geocode>
                    <cap:areaDesc>Sardegna</cap:areaDesc>
                    <cap:event>Orange Wind Warning</cap:event>
                    <cap:sent>2026-11-23T09:34:27+00:00</cap:sent>
                    <cap:onset>2026-11-23T09:00:00+00:00</cap:onset>
                    <cap:expires>2026-11-23T17:59:00+00:00</cap:expires>
                    <cap:identifier>2.49.0.0.380.3.IT.261123103426.071</cap:identifier>
                    <link type="application/cap+xml" href="https://feeds.meteoalarm.org/api/v1/warnings/y"/>
                  </entry>
                """.formatted(zone);
    }

    private static byte[] fixture(String name) {
        try (var in = MeteoAlarmFeedTest.class.getResourceAsStream("/meteoalarm/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
