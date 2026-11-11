package io.github.vincenzofiore.rootology.notifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.vincenzofiore.rootology.resilience.CircuitBreaker;
import io.github.vincenzofiore.rootology.resilience.CircuitBreakers;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Calls the Telegram Bot API (#38) through the plain Bot API over HTTPS
 * (https://core.telegram.org/bots/api): one message to one private chat, the bot's identity, and its updates
 * (a /start, and the blocking of the bot). Two clients with different read timeouts: a long poll cannot share
 * the short timeout of a send (#29).
 * <p>
 * The bot token sits in every request path, so this class is the only place that knows it and no exception
 * leaves it: a failure is reported with the service and, at most, the HTTP status and Telegram's error code,
 * never the message of an exception, which would carry the path. The clients are built with the static
 * {@link RestClient#builder()}, without an observation registry, so no metric tag can carry the path either.
 * The path is written whole rather than as a URI template: Spring would percent-encode the token's colon
 * ("123456%3Atoken") and Telegram would refuse it.
 */
@Component
class TelegramClient {

    static final String SERVICE = "Telegram";
    static final String UPDATES_SERVICE = "Telegram updates";

    /** What the bot listens to and nothing else: messages (the /start of a link) and being blocked (#38). */
    private static final String ALLOWED_UPDATES = "[\"message\",\"my_chat_member\"]";

    /** What became of one message, which decides whether the outbox keeps it for the next tick (#38). */
    enum Delivery {
        /** Telegram accepted it. */
        SENT,
        /** 403: the bot is blocked, the account deleted or the chat gone. The link is dead. */
        CHAT_GONE,
        /** Any other 4xx: this one message is lost, the chat is not (our own bug unlinks nobody). */
        DROPPED,
        /** 429: rate limited; the next tick is well after retry_after. Nothing is lost. */
        RETRY,
        /** 5xx, an I/O failure or an open circuit: try again later. */
        UNAVAILABLE
    }

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    private final RestClient sending;
    private final RestClient polling;
    /** The API up to the token: https://api.telegram.org/bot&lt;token&gt;. */
    private final String api;
    private final String token;
    private final long pollTimeout;
    private final CircuitBreaker breaker;
    private final CircuitBreaker pollBreaker;

    /** The bot's @username for the deep link, read from getMe once and never asked twice. */
    private volatile String username;

    @Autowired
    TelegramClient(@Value("${rootology.telegram.base-url}") String baseUrl,
                   @Value("${rootology.telegram.bot-token:}") String token,
                   @Value("${rootology.telegram.poll-timeout}") Duration pollTimeout,
                   CircuitBreakers circuits) {
        this(RestClient.builder().requestFactory(timeouts(Duration.ofSeconds(5))).build(),
                RestClient.builder().requestFactory(timeouts(pollTimeout.plusSeconds(15))).build(),
                baseUrl, token, pollTimeout.toSeconds(), circuits.forService(SERVICE),
                circuits.forService(UPDATES_SERVICE));
    }

    /** For tests: clients bound to a stub, no registry. */
    TelegramClient(RestClient sending, RestClient polling, String baseUrl, String token, long pollTimeout,
                   CircuitBreaker breaker, CircuitBreaker pollBreaker) {
        this.sending = sending;
        this.polling = polling;
        this.token = token == null ? "" : token.trim();
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.api = url + "/bot" + this.token;
        this.pollTimeout = pollTimeout;
        this.breaker = breaker;
        this.pollBreaker = pollBreaker;
    }

    private static SimpleClientHttpRequestFactory timeouts(Duration read) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(read);
        return timeouts;
    }

    /** False without a token: the feature is hidden and nothing is ever called (like GeoNames without an account). */
    boolean configured() {
        return !token.isEmpty();
    }

    /** The bot's @username, for the deep link the Notifications page shows; empty when Telegram does not answer. */
    Optional<String> botUsername() {
        if (!configured()) {
            return Optional.empty();
        }
        if (username != null) {
            return Optional.of(username);
        }
        if (!breaker.allowsCall()) {
            return Optional.empty();
        }
        Timer.Sample call = breaker.startCall();
        try {
            Bot bot = sending.get().uri(api + "/getMe").retrieve().body(Bot.class);
            if (bot == null || !bot.ok() || bot.result() == null || bot.result().username() == null) {
                breaker.failure(call);
                return Optional.empty();
            }
            breaker.success(call);
            username = bot.result().username();
            return Optional.of(username);
        } catch (RestClientException e) {
            breaker.failure(call);
            log.warn("Telegram did not answer getMe: {}", statusOf(e));
            return Optional.empty();
        }
    }

    /** Sends one plain-text message: no parse_mode, so nothing in it is ever read as formatting or a command. */
    Delivery send(long chatId, String text) {
        if (!configured() || !breaker.allowsCall()) {
            return Delivery.UNAVAILABLE;
        }
        Timer.Sample call = breaker.startCall();
        try {
            Reply reply = sending.post().uri(api + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SendMessage(chatId, text))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        Reply.Body body = null;
                        try {
                            body = response.bodyTo(Reply.Body.class);
                        } catch (RuntimeException notJson) {
                            // The status is what decides; a body we cannot read changes nothing.
                        }
                        return new Reply(status, body);
                    });
            return classify(reply, call);
        } catch (RestClientException e) {
            breaker.failure(call);
            log.warn("Telegram did not take a message: {}", statusOf(e));
            return Delivery.UNAVAILABLE;
        }
    }

    private Delivery classify(Reply reply, Timer.Sample call) {
        int status = reply.status();
        if (status >= 200 && status < 300) {
            breaker.success(call);
            return Delivery.SENT;
        }
        if (status == 429) {
            // Flood control: the service is fine and answers us, it just asked us to wait.
            breaker.success(call);
            log.info("Telegram asked to wait {} s before this chat again", reply.retryAfter());
            return Delivery.RETRY;
        }
        if (status >= 500) {
            // Telegram is unwell: the same message may be accepted later.
            breaker.failure(call);
            return Delivery.UNAVAILABLE;
        }
        if (status == 401) {
            // Our token is wrong or revoked: nothing to do with this chat, and dropping every message
            // silently would lose them all. The circuit opens and the admins are told (#27).
            breaker.failure(call);
            log.warn("Telegram refuses the bot token: check ROOTOLOGY_TELEGRAM_BOT_TOKEN");
            return Delivery.UNAVAILABLE;
        }
        breaker.success(call);
        if (status == 403) {
            log.info("Telegram refuses one chat (403): the bot is blocked or the chat is gone");
            return Delivery.CHAT_GONE;
        }
        // Our own request was wrong: one message of one chat, never a reason to unlink anybody.
        log.warn("Telegram refused one message: {} ({})", status, reply.errorCode());
        return Delivery.DROPPED;
    }

    /**
     * The bot's updates, long polling (https://core.telegram.org/bots/api#getupdates). Empty when the call
     * failed or the circuit is open: the next poll asks again, and the offset is not advanced.
     */
    Optional<List<Update>> updates(long offset) {
        if (!configured() || !pollBreaker.allowsCall()) {
            return Optional.empty();
        }
        Timer.Sample call = pollBreaker.startCall();
        try {
            Updates updates = polling.get()
                    .uri(api + "/getUpdates?offset={offset}&timeout={timeout}&allowed_updates={updates}",
                            offset, pollTimeout, ALLOWED_UPDATES)
                    .retrieve().body(Updates.class);
            if (updates == null || !updates.ok() || updates.result() == null) {
                pollBreaker.failure(call);
                return Optional.empty();
            }
            pollBreaker.success(call);
            return Optional.of(updates.result());
        } catch (RestClientException e) {
            pollBreaker.failure(call);
            log.warn("Telegram did not answer getUpdates: {}", statusOf(e));
            return Optional.empty();
        }
    }

    /** The HTTP status of a failed call, or its exception type: never its message, which carries the token. */
    private static String statusOf(RestClientException e) {
        return e instanceof RestClientResponseException response ? String.valueOf(response.getStatusCode().value())
                : e.getClass().getSimpleName();
    }

    private record SendMessage(@JsonProperty("chat_id") long chatId, String text) {
    }

    private record Reply(int status, Body body) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Body(boolean ok, @JsonProperty("error_code") Integer errorCode, Parameters parameters) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Parameters(@JsonProperty("retry_after") Integer retryAfter) {
        }

        Integer errorCode() {
            return body == null ? null : body.errorCode();
        }

        Integer retryAfter() {
            return body == null || body.parameters() == null ? null : body.parameters().retryAfter();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Bot(boolean ok, BotUser result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BotUser(String username) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Updates(boolean ok, List<Update> result) {
    }

    /** The parts of an update this app reads; everything else is ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Update(@JsonProperty("update_id") long id, Message message,
                  @JsonProperty("my_chat_member") ChatMemberUpdated myChatMember) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(Chat chat, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chat(long id, String type) {

        boolean privateChat() {
            return "private".equals(type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatMemberUpdated(Chat chat, @JsonProperty("new_chat_member") Member newChatMember) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Member(String status) {
    }
}
