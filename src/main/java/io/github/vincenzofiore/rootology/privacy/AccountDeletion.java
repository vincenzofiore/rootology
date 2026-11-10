package io.github.vincenzofiore.rootology.privacy;

import io.github.vincenzofiore.rootology.enrichment.ProposalPersonalData;
import io.github.vincenzofiore.rootology.garden.GardenPersonalData;
import io.github.vincenzofiore.rootology.history.RunPersonalData;
import io.github.vincenzofiore.rootology.notifications.NotificationPersonalData;
import io.github.vincenzofiore.rootology.notifications.TelegramPersonalData;
import io.github.vincenzofiore.rootology.pipeline.EscalationPersonalData;
import io.github.vincenzofiore.rootology.roles.RolePersonalData;
import io.github.vincenzofiore.rootology.spaces.SpacePersonalData;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.usage.UsagePersonalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A person deletes their account (#40, docs/privacy.md): their personal space and the spaces where they are
 * alone go with everything in them; their own records go; in what stays in shared spaces their login becomes
 * {@link #DELETED_USER}. One transaction: either all of it happens or nothing does.
 */
@Service
public class AccountDeletion {

    /** What a deleted person's login becomes in the records that stay. */
    public static final String DELETED_USER = "deleted user";

    /** What deleting would do, and why it cannot happen yet. */
    public record Preview(List<String> spacesDeleted, List<String> blockingSpaces, boolean lastAdmin) {
        public boolean allowed() {
            return blockingSpaces.isEmpty() && !lastAdmin;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AccountDeletion.class);

    private final SpacePersonalData spaces;
    private final GardenPersonalData gardens;
    private final RunPersonalData runs;
    private final EscalationPersonalData escalations;
    private final NotificationPersonalData notifications;
    private final TelegramPersonalData telegram;
    private final UsagePersonalData usage;
    private final ProposalPersonalData proposals;
    private final RolePersonalData roles;
    private final Clock clock;

    AccountDeletion(SpacePersonalData spaces, GardenPersonalData gardens, RunPersonalData runs,
                    EscalationPersonalData escalations, NotificationPersonalData notifications,
                    TelegramPersonalData telegram, UsagePersonalData usage,
                    ProposalPersonalData proposals, RolePersonalData roles, Clock clock) {
        this.spaces = spaces;
        this.gardens = gardens;
        this.runs = runs;
        this.escalations = escalations;
        this.notifications = notifications;
        this.telegram = telegram;
        this.usage = usage;
        this.proposals = proposals;
        this.roles = roles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Preview preview(String githubLogin) {
        String login = SpaceService.normalizeLogin(githubLogin);
        SpacePersonalData.DeletionPlan plan = spaces.plan(login);
        return new Preview(plan.spacesToDeleteNames(), plan.blockingSpaces(), roles.isLastAdmin(login));
    }

    /** Deletes the account; refuses while the preview says it is not allowed. */
    @Transactional
    public void delete(String githubLogin) {
        String login = SpaceService.normalizeLogin(githubLogin);
        SpacePersonalData.DeletionPlan plan = spaces.plan(login);
        List<String> refusals = new ArrayList<>();
        if (!plan.blockingSpaces().isEmpty()) {
            refusals.add("the only owner of " + plan.blockingSpaces() + ", which other people use");
        }
        if (roles.isLastAdmin(login)) {
            refusals.add("the last admin");
        }
        if (!refusals.isEmpty()) {
            throw new IllegalStateException("The account cannot be deleted: it is " + String.join(" and ", refusals));
        }
        Set<Long> gone = plan.spacesToDelete();
        // Everything that refers to a space first, the spaces and the account last.
        proposals.forget(login, gone, DELETED_USER);
        notifications.forget(login, gone, DELETED_USER);
        telegram.forget(login);
        escalations.forget(login, gone, DELETED_USER);
        runs.forget(login, gone, DELETED_USER);
        gardens.deleteSpaces(gone);
        usage.forget(login, DELETED_USER);
        roles.forget(login, DELETED_USER, clock.instant());
        spaces.forget(login, gone, DELETED_USER);
        log.info("An account was deleted, with {} space(s)", gone.size());
    }
}
