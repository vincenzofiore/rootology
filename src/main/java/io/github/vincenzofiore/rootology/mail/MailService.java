package io.github.vincenzofiore.rootology.mail;

import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/** Sends email in the background, retrying a few times; the caller never waits and never fails because of it. */
@Service
public class MailService {

    static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(2), Duration.ofSeconds(10));

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final String from;

    MailService(JavaMailSender sender, @Value("${rootology.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Async
    public void sendLater(OutgoingMail mail) {
        for (int attempt = 0; ; attempt++) {
            try {
                send(mail);
                log.info("Sent \"{}\" to {}", mail.subject(), mask(mail.to()));
                return;
            } catch (MailException | MessagingException e) {
                if (attempt >= BACKOFF.size()) {
                    log.warn("Could not send \"{}\" to {} after {} attempts: {}", mail.subject(), mask(mail.to()),
                            attempt + 1, e.getMessage());
                    return;
                }
                sleep(BACKOFF.get(attempt));
            }
        }
    }

    private void send(OutgoingMail mail) throws MessagingException {
        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(mail.to());
        helper.setSubject(mail.subject());
        helper.setText(mail.text(), mail.html());
        sender.send(message);
    }

    /** An address as logs show it, for example v***@example.org: no personal data in the logs. */
    public static String mask(String address) {
        int at = address == null ? -1 : address.indexOf('@');
        return at <= 0 ? "***" : address.charAt(0) + "***" + address.substring(at);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
