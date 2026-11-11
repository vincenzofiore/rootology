package io.github.vincenzofiore.rootology.notifications;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bot's side (#38): what it does with an update. Only a {@code /start} code in a private chat links, only a
 * blocking update unlinks, and nothing else changes anything — no command, no group, no chat id from a stranger.
 */
class TelegramUpdatesTest {

    static final String CODE = "abcdefghijklmnopqrstuvwxyz0123456789-_abcd";

    private final TelegramClient telegram = mock(TelegramClient.class);
    private final TelegramLinks links = mock(TelegramLinks.class);
    private final TelegramUpdates updates = new TelegramUpdates(telegram, links);

    private static TelegramClient.Update update(long id, String chatType, String text) {
        return new TelegramClient.Update(id, new TelegramClient.Message(new TelegramClient.Chat(42, chatType), text),
                null);
    }

    private static TelegramClient.Update blocked(long id, String chatType, String status) {
        return new TelegramClient.Update(id, null, new TelegramClient.ChatMemberUpdated(
                new TelegramClient.Chat(42, chatType), new TelegramClient.Member(status)));
    }

    @Test
    void aStartWithAGoodCodeLinksTheChatAndSaysSoOnce() {
        when(links.complete(CODE, 42L)).thenReturn(true);

        updates.handle(List.of(update(10, "private", "/start " + CODE)));

        verify(links).complete(CODE, 42L);
        verify(telegram).send(42L, TelegramMessages.connected());
        assertThat(updates.offset()).isEqualTo(11);
    }

    @Test
    void aStartWithAStaleCodeSaysHowToGetANewOne() {
        when(links.complete(CODE, 42L)).thenReturn(false);

        updates.handle(List.of(update(10, "private", "/start " + CODE)));

        verify(telegram).send(42L, TelegramMessages.codeExpired());
    }

    @Test
    void aMessageWithoutACodeIsAnsweredWithTheWayToLink() {
        updates.handle(List.of(update(10, "private", "ciao")));

        verify(links, never()).complete(anyString(), anyLong());
        verify(telegram).send(42L, TelegramMessages.notLinked());
    }

    @Test
    void aGroupIsNeverWritten() {
        updates.handle(List.of(update(10, "group", "/start " + CODE), update(11, "supergroup", "x")));

        verify(links, never()).complete(anyString(), anyLong());
        verify(telegram, never()).send(anyLong(), anyString());
        assertThat(updates.offset()).isEqualTo(12);
    }

    @Test
    void blockingTheBotMarksTheLinkButUnblockingChangesNothing() {
        updates.handle(List.of(blocked(10, "private", "kicked"), blocked(11, "private", "member"),
                blocked(12, "group", "kicked")));

        verify(links, times(1)).fail(42L);
        verify(telegram, never()).send(anyLong(), anyString());
    }

    @Test
    void onlyTheStartFormIsACode() {
        assertThat(TelegramUpdates.codeOf("/start " + CODE)).contains(CODE);
        assertThat(TelegramUpdates.codeOf("  /start " + CODE + "\n")).contains(CODE);
        assertThat(TelegramUpdates.codeOf("/start@rootology_bot " + CODE)).contains(CODE);
        assertThat(TelegramUpdates.codeOf("/start")).isEmpty();
        assertThat(TelegramUpdates.codeOf("/start short")).isEmpty();
        assertThat(TelegramUpdates.codeOf("/start " + CODE + " extra")).isEmpty();
        assertThat(TelegramUpdates.codeOf("/stop " + CODE)).isEmpty();
        assertThat(TelegramUpdates.codeOf(null)).isEmpty();
    }
}
