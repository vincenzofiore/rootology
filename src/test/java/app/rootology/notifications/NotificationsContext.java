package app.rootology.notifications;

import app.rootology.TestcontainersConfiguration;
import app.rootology.mail.MailService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * One Spring context for the notification tests (#174): the same properties, clock and doubles in every
 * subclass, so the context cache starts it once. A subclass sets {@link #now} in its own {@code @BeforeEach}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"rootology.notifications.digest-every=PT1H",
        "rootology.telegram.send-every=PT1H", "rootology.telegram.poll-every=PT1H"})
abstract class NotificationsContext {

    /** Static because the clock bean is shared by every test class of this context. */
    static Instant now = Instant.parse("2026-11-04T10:00:00Z");

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

    /** Never the real Telegram (#38): the tests say what each send answers. */
    @MockitoBean
    TelegramClient telegram;

    /** The real sender unless a test stubs it, as the digest failure tests do. */
    @MockitoSpyBean
    MailService mail;
}
