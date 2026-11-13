package app.rootology.notifications;

import app.rootology.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Linking a Telegram chat (#38) on a real database: a one-time code that only exists as a hash, its ten minutes,
 * one chat per person, and the link that Telegram stopped delivering to.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"rootology.telegram.poll-every=PT1H", "rootology.telegram.send-every=PT1H"})
class TelegramLinksTest {

    static Instant now = Instant.parse("2026-11-10T09:00:00Z");

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
    TelegramLinks links;

    @Autowired
    JdbcClient jdbc;

    @Test
    void aCodeLinksOnePrivateChatAndWorksOnce() {
        now = Instant.parse("2026-11-10T09:00:00Z");
        TelegramLinks.Start start = links.start("tg-ada");

        assertThat(start.code()).hasSize(43).matches(TelegramLinks.CODE);
        assertThat(start.url()).as("no bot token here: the app shows the code alone").isNull();
        String stored = jdbc.sql("select code_hash from telegram_link_code where login = 'tg-ada'")
                .query(String.class).single();
        assertThat(stored).as("only the SHA-256, never the code").hasSize(64).isNotEqualTo(start.code())
                .isEqualTo(TelegramLinks.hash(start.code()));

        assertThat(links.complete(start.code(), 555_000_111L)).isTrue();
        assertThat(links.of("tg-ada")).get().satisfies(link -> {
            assertThat(link.chatId()).isEqualTo(555_000_111L);
            assertThat(link.working()).isTrue();
        });
        assertThat(links.complete(start.code(), 555_000_222L)).as("used once").isFalse();
        assertThat(links.of("tg-ada").orElseThrow().chatId()).isEqualTo(555_000_111L);
    }

    @Test
    void aCodeOlderThanTenMinutesIsRefused() {
        now = Instant.parse("2026-11-10T09:00:00Z");
        TelegramLinks.Start start = links.start("tg-bruno");

        now = now.plus(Duration.ofMinutes(11));

        assertThat(links.complete(start.code(), 555_000_333L)).isFalse();
        assertThat(links.of("tg-bruno")).isEmpty();
    }

    @Test
    void aSecondCodeReplacesTheFirstAndAChatBelongsToOnePerson() {
        now = Instant.parse("2026-11-10T09:00:00Z");
        TelegramLinks.Start first = links.start("tg-carla");
        TelegramLinks.Start second = links.start("tg-carla");
        assertThat(links.complete(first.code(), 555_000_444L)).as("asking again kills the old code").isFalse();
        assertThat(links.complete(second.code(), 555_000_444L)).isTrue();

        TelegramLinks.Start other = links.start("tg-dino");
        assertThat(links.complete(other.code(), 555_000_444L)).isTrue();
        assertThat(links.of("tg-carla")).as("the chat moved to another person").isEmpty();
        assertThat(links.of("tg-dino").orElseThrow().chatId()).isEqualTo(555_000_444L);
    }

    @Test
    void aFailedLinkIsKeptAndAWorkingOneCanBeRemoved() {
        now = Instant.parse("2026-11-10T09:00:00Z");
        TelegramLinks.Start start = links.start("tg-elio");
        assertThat(links.complete(start.code(), 555_000_555L)).isTrue();

        links.fail(555_000_555L);
        assertThat(links.of("tg-elio")).get().satisfies(link -> assertThat(link.working()).isFalse());

        links.unlink("tg-elio");
        assertThat(links.of("tg-elio")).isEmpty();
    }
}
