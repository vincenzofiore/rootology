package app.rootology.privacy;

import app.rootology.access.AccessPersonalData;
import app.rootology.access.AccountFlagPersonalData;
import app.rootology.enrichment.ProposalPersonalData;
import app.rootology.garden.GardenPersonalData;
import app.rootology.history.RunPersonalData;
import app.rootology.notifications.NotificationPersonalData;
import app.rootology.notifications.TelegramPersonalData;
import app.rootology.pipeline.EscalationPersonalData;
import app.rootology.roles.RolePersonalData;
import app.rootology.spaces.SpacePersonalData;
import app.rootology.usage.UsagePersonalData;
import org.springframework.stereotype.Component;

/**
 * The {@code *PersonalData} classes of every module in one place (#40): export, account deletion and the
 * retention job all work through them, and none of them is called from anywhere else.
 */
@Component
record PersonalData(SpacePersonalData spaces, GardenPersonalData gardens, RunPersonalData runs,
                    EscalationPersonalData escalations, NotificationPersonalData notifications,
                    TelegramPersonalData telegram, UsagePersonalData usage, ProposalPersonalData proposals,
                    RolePersonalData roles, AccessPersonalData access, AccountFlagPersonalData accountFlags) {
}
