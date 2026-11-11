package io.github.vincenzofiore.rootology.notifications;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The bot's side of the link (#38): long polling of getUpdates, with no webhook and so no public route. It
 * reads exactly two things and changes nothing else: the {@code /start <code>} of a person linking their chat,
 * and the update Telegram sends when the bot is blocked or unblocked in a private chat
 * (https://core.telegram.org/bots/api#update). Any other message is ignored; there is no command that changes data.
 */
@Component
class TelegramUpdates {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TelegramUpdates.class);
    private static final Pattern START = Pattern.compile("/start(?:@\\w+)?\\s+(" + TelegramLinks.CODE + ")");

    private final TelegramClient telegram;
    private final TelegramLinks links;

    /** The next update to ask for; in memory, so a restart may see an old code once (expired or used: harmless). */
    private long offset;

    TelegramUpdates(TelegramClient telegram, TelegramLinks links) {
        this.telegram = telegram;
        this.links = links;
    }

    /** One long poll, one batch of updates; without a token nothing runs at all. */
    @Scheduled(fixedDelayString = "${rootology.telegram.poll-every}", initialDelayString = "PT10S")
    void poll() {
        if (!telegram.configured()) {
            return;
        }
        telegram.updates(offset).ifPresent(this::handle);
    }

    /** Applies a batch of updates in order, remembering where to continue; the tests call this directly. */
    void handle(List<TelegramClient.Update> updates) {
        for (TelegramClient.Update update : updates) {
            offset = Math.max(offset, update.id() + 1);
            if (update.message() != null) {
                link(update.message());
            }
            if (update.myChatMember() != null) {
                blocked(update.myChatMember());
            }
        }
    }

    private void link(TelegramClient.Message message) {
        if (message.chat() == null || !message.chat().privateChat()) {
            return; // only private chats are ever written (#38)
        }
        long chatId = message.chat().id();
        Optional<String> code = codeOf(message.text());
        if (code.isEmpty()) {
            telegram.send(chatId, TelegramMessages.notLinked());
            return;
        }
        if (links.complete(code.get(), chatId)) {
            telegram.send(chatId, TelegramMessages.connected());
        } else {
            telegram.send(chatId, TelegramMessages.codeExpired());
        }
    }

    /** In a private chat Telegram sends this update only when the bot is blocked or unblocked. */
    private void blocked(TelegramClient.ChatMemberUpdated change) {
        if (change.chat() != null && change.chat().privateChat()
                && change.newChatMember() != null && "kicked".equals(change.newChatMember().status())) {
            links.fail(change.chat().id());
        }
    }

    /** The code of a {@code /start <code>} message, and nothing else: the text is untrusted input. */
    static Optional<String> codeOf(String text) {
        if (text == null) {
            return Optional.empty();
        }
        var found = START.matcher(text.strip());
        return found.matches() ? Optional.of(found.group(1)) : Optional.empty();
    }

    long offset() {
        return offset;
    }
}
