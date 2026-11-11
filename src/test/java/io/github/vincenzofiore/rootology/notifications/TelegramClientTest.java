package io.github.vincenzofiore.rootology.notifications;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.vincenzofiore.rootology.resilience.CircuitBreaker;
import io.github.vincenzofiore.rootology.resilience.CircuitBreakers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The Bot API client (#38), without calling Telegram: what it sends, how it reads Telegram's answers, and that
 * the token, which sits in every request path, reaches neither a log line nor a metric.
 */
class TelegramClientTest {

    /** A token shaped like a real one, so the path is realistic; never a real token. */
    static final String TOKEN = "123456789:AAH-test-token-not-real";
    static final long CHAT = 9876543210L;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final CircuitBreakers circuits = new CircuitBreakers(meters);
    private final CircuitBreaker sends = circuits.forService(TelegramClient.SERVICE);
    private final CircuitBreaker polls = circuits.forService(TelegramClient.UPDATES_SERVICE);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    private MockRestServiceServer server;
    private TelegramClient client;

    @BeforeEach
    void clientWithAStub() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient stub = builder.build();
        client = new TelegramClient(stub, stub, "https://api.telegram.org", TOKEN, 25, sends, polls);
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void release() {
        root.detachAppender(logs);
        logs.stop();
    }

    @Test
    void aMessageIsPlainTextToThePrivateChat() {
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"chat_id\":9876543210,\"text\":\"Rootology — ciao\"}", true))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{}}", MediaType.APPLICATION_JSON));

        assertThat(client.send(CHAT, "Rootology — ciao")).isEqualTo(TelegramClient.Delivery.SENT);
        server.verify();
        assertThat(sends.failing()).isFalse();
    }

    @Test
    void onlyFourZeroThreeMeansTheChatIsGone() {
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was blocked by the user\"}"));
        assertThat(client.send(CHAT, "x")).isEqualTo(TelegramClient.Delivery.CHAT_GONE);

        // Another 4xx is our own bad request: one message lost, the chat is fine, nobody is unlinked.
        server.reset();
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}"));
        assertThat(client.send(CHAT, "x")).isEqualTo(TelegramClient.Delivery.DROPPED);
        assertThat(sends.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void aRefusedTokenIsNeverMistakenForOneBadChat() {
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized\"}"));

        assertThat(client.send(CHAT, "x")).as("a bad token loses nothing silently").isEqualTo(
                TelegramClient.Delivery.UNAVAILABLE);
        assertThat(sends.failing()).isTrue();
    }

    @Test
    void aRateLimitAsksForPatienceAndKeepsTheCircuitClosed() {
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\","
                                + "\"parameters\":{\"retry_after\":7}}"));

        assertThat(client.send(CHAT, "x")).isEqualTo(TelegramClient.Delivery.RETRY);
        assertThat(sends.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(logged()).anyMatch(line -> line.contains("7"));
    }

    @Test
    void aFailingTelegramOpensTheCircuitAfterThreeTries() {
        server.expect(ExpectedCount.times(CircuitBreaker.FAILURE_THRESHOLD),
                        requestTo("https://api.telegram.org/bot" + TOKEN + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        for (int i = 0; i < CircuitBreaker.FAILURE_THRESHOLD; i++) {
            assertThat(client.send(CHAT, "x")).isEqualTo(TelegramClient.Delivery.UNAVAILABLE);
        }
        assertThat(sends.state()).isEqualTo(CircuitBreaker.State.OPEN);
        // Open: no call is made, and nothing waits on a timeout.
        assertThat(client.send(CHAT, "x")).isEqualTo(TelegramClient.Delivery.UNAVAILABLE);
    }

    @Test
    void theUsernameComesFromGetMeOnceAndStaysCached() {
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/getMe"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"id\":1,\"username\":\"rootology_bot\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.botUsername()).contains("rootology_bot");
        assertThat(client.botUsername()).as("asked once").contains("rootology_bot");
        server.verify();
    }

    @Test
    void withoutATokenNothingIsEverCalled() {
        TelegramClient unconfigured = new TelegramClient(RestClient.create(), RestClient.create(),
                "https://api.telegram.org", " ", 25, sends, polls);

        assertThat(unconfigured.configured()).isFalse();
        assertThat(unconfigured.send(CHAT, "x")).isEqualTo(TelegramClient.Delivery.UNAVAILABLE);
        assertThat(unconfigured.botUsername()).isEmpty();
        assertThat(unconfigured.updates(0)).isEmpty();
        server.verify(); // no request was made
    }

    @Test
    void theUpdatesCarryTheOffsetAndOnlyTheTwoKindsWeRead() {
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("https://api.telegram.org/bot" + TOKEN + "/getUpdates"),
                        org.hamcrest.Matchers.containsString("offset=41"),
                        org.hamcrest.Matchers.containsString("timeout=25"))))
                .andRespond(withSuccess("{\"ok\":true,\"result\":[{\"update_id\":41,"
                                + "\"message\":{\"message_id\":1,\"chat\":{\"id\":42,\"type\":\"private\"},"
                                + "\"text\":\"/start abc\"}}]}", MediaType.APPLICATION_JSON));

        Optional<List<TelegramClient.Update>> updates = client.updates(41);

        assertThat(updates).isPresent();
        assertThat(updates.get()).singleElement().satisfies(update -> {
            assertThat(update.id()).isEqualTo(41);
            assertThat(update.message().chat().privateChat()).isTrue();
            assertThat(update.message().text()).isEqualTo("/start abc");
        });
    }

    @Test
    void theTokenAndTheChatIdReachNeitherTheLogsNorTheMetrics() {
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":403}"));
        server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/getMe"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        client.send(CHAT, "x");
        client.botUsername();

        assertThat(logged()).isNotEmpty()
                .noneMatch(line -> line.contains(TOKEN))
                .noneMatch(line -> line.contains(String.valueOf(CHAT)));
        List<String> meterIds = meters.getMeters().stream().map(meter -> meter.getId() + " " + meter.getId().getTags())
                .toList();
        assertThat(meterIds).isNotEmpty().noneMatch(id -> id.contains(TOKEN) || id.contains(String.valueOf(CHAT)));
        // No observation registry: the static RestClient records no http.client.requests at all.
        assertThat(meters.find("http.client.requests").meters()).isEmpty();
    }

    private List<String> logged() {
        return logs.list.stream().flatMap(e -> Stream.concat(
                        Stream.of(e.getFormattedMessage(), e.getThrowableProxy() == null ? null
                                : e.getThrowableProxy().getMessage()),
                        e.getMDCPropertyMap().values().stream()))
                .filter(Objects::nonNull).toList();
    }
}
