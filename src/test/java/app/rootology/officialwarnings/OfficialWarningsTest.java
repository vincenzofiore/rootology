package app.rootology.officialwarnings;

import app.rootology.resilience.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The warnings in memory: which CAP documents are read, what is dropped, and how old the list may get. */
class OfficialWarningsTest {

    private static final String FEED_URL = "https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-italy";
    private static final String FEED_TEXT = text("feed-italy.xml");
    private static final byte[] FEED = FEED_TEXT.getBytes(StandardCharsets.UTF_8);
    private static final byte[] CAP = bytes("cap-calabria-rain.xml");
    private static final List<String> CAP_URLS = capUrls();
    private static final Instant START = Instant.parse("2026-11-23T10:00:00Z");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final MutableClock clock = new MutableClock(START);
    private final OfficialWarnings warnings = new OfficialWarnings(
            new MeteoAlarmClient(builder.build(), URI.create("https://feeds.meteoalarm.org"),
                    new CircuitBreaker("test meteoalarm")), clock);

    @Test
    void theFirstReadReadsEachCapDocumentOnceAndTheNextOneReadsNone() {
        expectFeed();
        CAP_URLS.forEach(url -> expectCap(url));
        warnings.refresh();
        server.verify();

        assertThat(warnings.active("IT001")).hasSize(3);
        assertThat(warnings.active("IT019")).hasSize(1);
        assertThat(warnings.active("IT001").getFirst().headline())
                .isEqualTo("Allerta Pioggia Giallo per l'Italia - Calabria");
        assertThat(warnings.lastRead()).contains(START);
        assertThat(warnings.stale()).isFalse();

        // The same feed again: every warning is known, so no CAP document is fetched.
        server.reset();
        expectFeed();
        warnings.refresh();
        server.verify();
        assertThat(warnings.active("IT001").getFirst().headline())
                .isEqualTo("Allerta Pioggia Giallo per l'Italia - Calabria");
    }

    @Test
    void aWarningGoneFromTheFeedDisappears() {
        expectFeed();
        CAP_URLS.forEach(url -> expectCap(url));
        warnings.refresh();
        server.verify();

        server.reset();
        // Only the Sardegna warning is left in the feed.
        expectFeed(FEED_TEXT.replace(entry(FEED_TEXT, 0), "").replace(entry(FEED_TEXT, 2), "")
                .replace(entry(FEED_TEXT, 3), "").getBytes(StandardCharsets.UTF_8));
        warnings.refresh();
        server.verify();

        assertThat(warnings.active("IT001")).isEmpty();
        assertThat(warnings.active("IT019")).hasSize(1);
    }

    @Test
    void anExpiredWarningIsNotReturned() {
        expectFeed();
        CAP_URLS.forEach(url -> expectCap(url));
        warnings.refresh();
        server.verify();
        assertThat(warnings.active("IT019")).hasSize(1);

        clock.set(Instant.parse("2026-11-23T18:00:00Z"));

        assertThat(warnings.active("IT019")).isEmpty();
        assertThat(warnings.active("IT001")).hasSize(2);
    }

    @Test
    void aWarningWithTheHighestLevelComesFirstThenTheEarliestOnset() {
        String mild = "https://feeds.meteoalarm.org/api/v1/warnings/feeds-italy/mild";
        String grave = "https://feeds.meteoalarm.org/api/v1/warnings/feeds-italy/grave";
        expectFeed(feed(entry("IT001", "2026-11-25T00:00:00Z", "2026-11-26T00:00:00Z", mild),
                entry("IT001", "2026-11-24T00:00:00Z", "2026-11-26T00:00:00Z", grave)));
        server.expect(ExpectedCount.once(), requestTo(mild))
                .andRespond(withSuccess(cap(2, "Allerta Pioggia Giallo"), MediaType.APPLICATION_XML));
        server.expect(ExpectedCount.once(), requestTo(grave))
                .andRespond(withSuccess(cap(4, "Allerta Pioggia Rosso"), MediaType.APPLICATION_XML));

        warnings.refresh();
        server.verify();

        assertThat(warnings.active("IT001")).extracting(OfficialWarning::headline)
                .containsExactly("Allerta Pioggia Rosso", "Allerta Pioggia Giallo");
    }

    @Test
    void aFailingFeedKeepsTheLastList() {
        expectFeed();
        CAP_URLS.forEach(url -> expectCap(url));
        warnings.refresh();
        server.verify();

        server.reset();
        server.expect(ExpectedCount.once(), requestTo(FEED_URL)).andRespond(withServerError());
        warnings.refresh();
        server.verify();

        assertThat(warnings.active("IT001")).hasSize(3);
        assertThat(warnings.lastRead()).contains(START);
    }

    @Test
    void aCapDocumentThatCannotBeReadIsRetriedAtTheNextRefresh() {
        String first = CAP_URLS.getFirst();
        expectFeed();
        server.expect(ExpectedCount.once(), requestTo(first)).andRespond(withServerError());
        CAP_URLS.subList(1, CAP_URLS.size()).forEach(this::expectCap);
        warnings.refresh();
        server.verify();
        assertThat(warnings.active("IT001")).filteredOn(w -> w.headline() == null).hasSize(1);

        server.reset();
        expectFeed();
        expectCap(first);
        warnings.refresh();
        server.verify();
        assertThat(warnings.active("IT001")).allSatisfy(w -> assertThat(w.headline()).isNotNull());
    }

    @Test
    void staleAfterTenMinutesWithoutAReadOrWithNoneAtAll() {
        assertThat(warnings.stale()).isTrue();

        expectFeed();
        CAP_URLS.forEach(url -> expectCap(url));
        warnings.refresh();
        server.verify();

        clock.set(START.plusSeconds(9 * 60));
        assertThat(warnings.stale()).isFalse();
        clock.set(START.plusSeconds(10 * 60));
        assertThat(warnings.stale()).isTrue();
    }

    @Test
    void aCapDocumentOnAnotherHostIsNotFetched() {
        expectFeed(feed(entry("IT001", "2026-11-24T00:00:00Z", "2026-11-26T00:00:00Z",
                "https://elsewhere.example/cap/1")));

        warnings.refresh();
        server.verify(); // only the feed: no request leaves MeteoAlarm

        assertThat(warnings.active("IT001")).hasSize(1);
        assertThat(warnings.active("IT001").getFirst().headline()).isNull();
    }

    private void expectFeed() {
        expectFeed(FEED);
    }

    private void expectFeed(byte[] feed) {
        server.expect(ExpectedCount.once(), requestTo(FEED_URL))
                .andRespond(withSuccess(feed, MediaType.APPLICATION_XML));
    }

    private void expectCap(String url) {
        server.expect(ExpectedCount.once(), requestTo(url))
                .andRespond(withSuccess(CAP, MediaType.APPLICATION_XML));
    }

    private static byte[] feed(String... entries) {
        String head = FEED_TEXT.substring(0, FEED_TEXT.indexOf("<entry>"));
        return (head + String.join("", entries) + "</feed>").getBytes(StandardCharsets.UTF_8);
    }

    private static String entry(String zone, String onset, String expires, String capUrl) {
        return """
                  <entry>
                    <cap:geocode><valueName>EMMA_ID</valueName><value>%s</value></cap:geocode>
                    <cap:areaDesc>Calabria</cap:areaDesc>
                    <cap:event>Yellow Rain Warning</cap:event>
                    <cap:sent>%s</cap:sent>
                    <cap:onset>%s</cap:onset>
                    <cap:expires>%s</cap:expires>
                    <cap:severity>Moderate</cap:severity>
                    <cap:identifier>2.49.0.0.380.3.IT.%s</cap:identifier>
                    <link type="application/cap+xml" href="%s"/>
                  </entry>
                """.formatted(zone, onset, onset, expires, capUrl.substring(capUrl.lastIndexOf('/') + 1), capUrl);
    }

    /** A CAP document with only the fields the warnings use, in English and in Italian. */
    private static byte[] cap(int level, String headline) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <info>
                    <language>en-GB</language>
                    <headline>English headline</headline>
                    <parameter><valueName>awareness_level</valueName><value>%d; yellow; Moderate</value></parameter>
                    <parameter><valueName>awareness_type</valueName><value>10; Rain</value></parameter>
                  </info>
                  <info>
                    <language>it-IT</language>
                    <senderName>Servizio Meteorologico dell'Aeronautica Militare</senderName>
                    <headline>%s</headline>
                    <description>Fenomeni di moderata intensità</description>
                    <instruction>STARE ATTENTI</instruction>
                    <parameter><valueName>awareness_level</valueName><value>%d; yellow; Moderate</value></parameter>
                    <parameter><valueName>awareness_type</valueName><value>10; Rain</value></parameter>
                  </info>
                </alert>
                """.formatted(level, headline, level).getBytes(StandardCharsets.UTF_8);
    }

    private static String entry(String feedText, int index) {
        Matcher matcher = Pattern.compile("(?s)  <entry>.*?</entry>\n").matcher(feedText);
        for (int i = 0; i <= index; i++) {
            if (!matcher.find()) {
                throw new IllegalStateException("the feed has no entry " + index);
            }
        }
        return matcher.group();
    }

    private static List<String> capUrls() {
        try {
            return MeteoAlarmFeed.parseFeed(FEED).stream().map(OfficialWarning::capUrl).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String text(String name) {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String name) {
        try (var in = OfficialWarningsTest.class.getResourceAsStream("/meteoalarm/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A clock the test moves by hand: "now" in the service, never the machine's time. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
