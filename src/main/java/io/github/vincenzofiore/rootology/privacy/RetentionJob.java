package io.github.vincenzofiore.rootology.privacy;

import io.github.vincenzofiore.rootology.history.RunPersonalData;
import io.github.vincenzofiore.rootology.notifications.NotificationPersonalData;
import io.github.vincenzofiore.rootology.notifications.TelegramPersonalData;
import io.github.vincenzofiore.rootology.pipeline.EscalationPersonalData;
import io.github.vincenzofiore.rootology.spaces.SpacePersonalData;
import io.github.vincenzofiore.rootology.usage.UsagePersonalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Every night, deletes what is older than the retention periods (`rootology.retention`, shown on the privacy
 * page). Weather keeps its own 30 days in the weather module.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final Retention retention;
    private final RunPersonalData runs;
    private final EscalationPersonalData escalations;
    private final NotificationPersonalData notifications;
    private final TelegramPersonalData telegram;
    private final UsagePersonalData usage;
    private final SpacePersonalData spaces;
    private final Clock clock;

    RetentionJob(Retention retention, RunPersonalData runs, EscalationPersonalData escalations,
                 NotificationPersonalData notifications, TelegramPersonalData telegram, UsagePersonalData usage,
                 SpacePersonalData spaces, Clock clock) {
        this.retention = retention;
        this.runs = runs;
        this.escalations = escalations;
        this.notifications = notifications;
        this.telegram = telegram;
        this.usage = usage;
        this.spaces = spaces;
        this.clock = clock;
    }

    @Scheduled(cron = "${rootology.retention.cron}", zone = "Europe/Rome")
    @Transactional
    public void run() {
        Instant now = clock.instant();
        int deleted = runs.purge(now.minus(retention.history()))
                + escalations.purge(now.minus(retention.escalations()))
                + notifications.purge(now.minus(retention.notifications()))
                + usage.purge(now.minus(retention.usage()))
                + spaces.purgeInvitations(now.minus(retention.invitations()))
                + telegram.purge(now.minus(retention.telegram()));
        log.info("Retention: {} old record(s) deleted", deleted);
    }
}
