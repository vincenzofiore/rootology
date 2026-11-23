package app.rootology.spaces;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The block of a whole account (#165), kept on {@code app_user}: an admin takes away a person's access to the
 * app and writes why, and unblocking forgets all three columns (who unblocked and when stay in the log only).
 * Nothing else of the person changes. The block is read at sign-in, on every navigation and on the person's
 * own page, so that read stays one cheap query.
 */
@Component
public class AccountBlocks {

    private final AppUserRepository users;

    AccountBlocks(AppUserRepository users) {
        this.users = users;
    }

    /** True while the account is blocked: the one query every sign-in and navigation makes. */
    @Transactional(readOnly = true)
    public boolean blocked(String githubLogin) {
        return users.existsByLoginAndBlockedAtIsNotNull(SpaceService.normalizeLogin(githubLogin));
    }

    /** The block of a person, empty while the account is open. */
    @Transactional(readOnly = true)
    public Optional<AccountBlock> of(String githubLogin) {
        return findByLogin(githubLogin).filter(AppUser::isBlocked).map(AppUser::accountBlock);
    }

    /** Every blocked account, by login, for the admin page. */
    @Transactional(readOnly = true)
    public Map<String, AccountBlock> all() {
        return users.findByBlockedAtIsNotNull().stream()
                .collect(Collectors.toMap(AppUser::getLogin, AppUser::accountBlock));
    }

    /** Blocks the account: it keeps everything it has and only loses the app. */
    @Transactional
    public void block(String githubLogin, String requester, String reason, Instant now) {
        AppUser user = findByLogin(githubLogin)
                .orElseThrow(() -> new SpaceAccessException(githubLogin + " is not a person of the app"));
        if (user.isBlocked()) {
            throw new IllegalStateException(githubLogin + " is already blocked");
        }
        user.block(SpaceService.normalizeLogin(requester), reason, now);
    }

    /** Unblocks the account, so that it works exactly as before. */
    @Transactional
    public void unblock(String githubLogin) {
        AppUser user = findByLogin(githubLogin).filter(AppUser::isBlocked)
                .orElseThrow(() -> new SpaceAccessException(githubLogin + " is not blocked"));
        // One statement, and the loaded entity is dropped with it: nothing keeps a stale block around.
        users.clearBlock(user.getId());
    }

    private Optional<AppUser> findByLogin(String githubLogin) {
        return users.findByLogin(SpaceService.normalizeLogin(githubLogin));
    }
}
