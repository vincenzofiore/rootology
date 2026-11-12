package app.rootology.notifications;

import app.rootology.TestcontainersConfiguration;
import app.rootology.access.AccessRequestService;
import app.rootology.spaces.IdentityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The admins are told of a new invitation request (#65) through the same outbox as everything else: one row
 * each with no space, an email digest and a Telegram message that say only that something waits, never the
 * motivation, the login or the address.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"rootology.notifications.digest-every=PT1H",
        "rootology.telegram.send-every=PT1H", "rootology.telegram.poll-every=PT1H"})
class AccessNotificationsTest {

    static final Instant START = Instant.parse("2026-11-04T10:00:00Z");

    /** Moved forward before a digest: emails wait for the grouping window, Telegram does not. */
    static Instant now = START;

    @TestBean
    Clock clock;

    static Clock clock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("Europe/Rome");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
    }

    @Autowired
    AccessRequestService requests;

    @Autowired
    NotificationService notifications;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    TelegramClient telegram;

    @Value("${rootology.test.mailpit-api}")
    String mailpit;

    /** Every test starts from no request: the daily cap (#65) counts the requests of the whole day. */
    @BeforeEach
    void clean() {
        jdbc.sql("delete from access_request").update();
    }

    private void admin(String login) {
        jdbc.sql("""
                        insert into app_role_grant (github_login, role, granted_by, granted_at)
                        values (:login, 'ADMIN', 'setup', now())""")
                .param("login", login).update();
        jdbc.sql("""
                        insert into notification_preference (github_login, email) values (:login, :email)
                        on conflict (github_login) do update set email = excluded.email""")
                .param("login", login).param("email", login + "@example.org").update();
    }

    /** A working chat, for the one test that reads what Telegram would say. */
    private void telegramLinked(String login) {
        jdbc.sql("insert into telegram_link (login, chat_id, linked_at) values (:login, :chat, now()) "
                        + "on conflict (login) do update set chat_id = excluded.chat_id, failed_at = null")
                .param("login", login).param("chat", 800_000_000L + Math.abs(login.hashCode())).update();
    }

    @Test
    void aNewRequestCreatesOneRowPerAdminWithNoSpace() {
        now = START;
        admin("acc-admin-one");
        admin("acc-admin-two");

        requests.request(IdentityProvider.GITHUB, "acc-notify-1", "acc-asker", "asker@example.org",
                "A private reason nobody else should read");

        assertThat(notifications.of("acc-admin-one", 10)).anySatisfy(n -> {
            assertThat(n.accessRequest()).isTrue();
            assertThat(n.spaceId()).isZero();
            assertThat(n.unread()).isTrue();
        });
        assertThat(notifications.of("acc-admin-two", 10)).anySatisfy(n -> assertThat(n.accessRequest()).isTrue());
        assertThat(jdbc.sql("select count(*) from notification where kind = 'ACCESS_REQUEST' "
                        + "and recipient = 'acc-admin-one' and space_id is null").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void theEmailAndTheTelegramMessageOnlySayThatSomethingWaits() throws InterruptedException {
        now = START;
        admin("acc-admin-mail");
        telegramLinked("acc-admin-mail");
        when(telegram.send(anyLong(), anyString())).thenReturn(TelegramClient.Delivery.SENT);

        requests.request(IdentityProvider.GITHUB, "acc-notify-2", "acc-asker-two", "asker2@example.org",
                "My tomatoes need help");

        now = START.plus(Duration.ofMinutes(3));
        assertThat(notifications.digest()).isGreaterThanOrEqualTo(1);
        JsonNode mail = messageTo("acc-admin-mail@example.org");
        for (String part : new String[]{"Text", "HTML"}) {
            assertThat(mail.path(part).asString()).as(part + ": the page, and nothing else")
                    .contains("/admin")
                    .doesNotContain("My tomatoes need help", "acc-asker-two", "asker2@example.org");
        }

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        assertThat(notifications.telegramDigest()).isGreaterThanOrEqualTo(1);
        verify(telegram).send(anyLong(), text.capture());
        assertThat(text.getValue()).contains("richiesta di invito", "/admin")
                .doesNotContain("My tomatoes need help", "acc-asker-two", "asker2@example.org");
    }

    /** The full message Mailpit received for this address (subject, Text, HTML), as NotificationServiceTest does. */
    private JsonNode messageTo(String address) throws InterruptedException {
        RestClient api = RestClient.create(mailpit);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            JsonNode found = JsonMapper.builder().build().readTree(api.get()
                    .uri(u -> u.path("/api/v1/search").queryParam("query", "to:\"" + address + "\"").build())
                    .retrieve().body(String.class));
            if (found.path("messages_count").asInt() > 0) {
                String id = found.path("messages").get(0).path("ID").asString();
                return JsonMapper.builder().build().readTree(api.get()
                        .uri("/api/v1/message/" + id).retrieve().body(String.class));
            }
            Thread.sleep(250);
        }
        throw new AssertionError("No email to " + address);
    }
}
