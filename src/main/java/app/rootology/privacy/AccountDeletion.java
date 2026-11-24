package app.rootology.privacy;

import app.rootology.roles.RoleService;
import app.rootology.spaces.SpacePersonalData;
import app.rootology.spaces.SpaceService;
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

    private final PersonalData people;
    private final SpaceService spaces;
    private final RoleService roles;
    private final Clock clock;

    AccountDeletion(PersonalData people, SpaceService spaces, RoleService roles, Clock clock) {
        this.people = people;
        this.spaces = spaces;
        this.roles = roles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Preview preview(String githubLogin) {
        String login = SpaceService.normalizeLogin(githubLogin);
        SpacePersonalData.DeletionPlan plan = people.spaces().plan(login);
        return new Preview(plan.spacesToDeleteNames(), plan.blockingSpaces(), roles.isLastAdmin(login));
    }

    /** Deletes the account; refuses while the preview says it is not allowed. */
    @Transactional
    public void delete(String githubLogin) {
        String login = SpaceService.normalizeLogin(githubLogin);
        SpacePersonalData.DeletionPlan plan = people.spaces().plan(login);
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
        people.access().forget(login);
        people.accountFlags().forget(login, DELETED_USER);
        people.proposals().forget(login, gone, DELETED_USER);
        people.notifications().forget(login, gone, DELETED_USER);
        people.telegram().forget(login);
        people.escalations().forget(login, gone, DELETED_USER);
        people.runs().forget(login, gone, DELETED_USER);
        people.gardens().forget(login, gone, DELETED_USER);
        people.gardens().deleteSpaces(gone);
        people.usage().forget(login, DELETED_USER);
        people.roles().forget(login, DELETED_USER, clock.instant());
        people.spaces().forget(login, gone, DELETED_USER);
        log.info("An account was deleted, with {} space(s)", gone.size());
    }
}
