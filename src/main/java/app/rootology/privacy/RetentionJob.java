package app.rootology.privacy;

import app.rootology.access.AccessRequestService;
import app.rootology.history.RunPersonalData;
import app.rootology.notifications.NotificationPersonalData;
import app.rootology.notifications.TelegramPersonalData;
import app.rootology.pipeline.EscalationPersonalData;
import app.rootology.spaces.SpacePersonalData;
import app.rootology.usage.UsagePersonalData;
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
    private final AccessRequestService accessRequests;
    private final Clock clock;

    RetentionJob(Retention retention, RunPersonalData runs, EscalationPersonalData escalations,
                 NotificationPersonalData notifications, TelegramPersonalData telegram, UsagePersonalData usage,
                 SpacePersonalData spaces, AccessRequestService accessRequests, Clock clock) {
        this.retention = retention;
        this.runs = runs;
        this.escalations = escalations;
        this.notifications = notifications;
        this.telegram = telegram;
        this.usage = usage;
        this.spaces = spaces;
        this.accessRequests = accessRequests;
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
                + telegram.purge(now.minus(retention.telegram()))
                + accessRequests.purge(now.minus(retention.rejectedAccessRequests()),
                        now.minus(retention.pendingAccessRequests()), now.minus(retention.pendingAccessRequests()));
        log.info("Retention: {} old record(s) deleted", deleted);
    }
}
