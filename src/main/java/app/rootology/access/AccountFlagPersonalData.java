package app.rootology.access;

import app.rootology.spaces.SpaceService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A person's account flags (#40, #165), for export, account deletion and nothing else: an admin decides a flag,
 * and only the admin pages read it. Deleting an account removes its flags, and a decision it made names no one.
 */
@Component
public class AccountFlagPersonalData {

    private final AccountFlagRepository flags;

    AccountFlagPersonalData(AccountFlagRepository flags) {
        this.flags = flags;
    }

    public List<AccountFlagView> export(String login) {
        return flags.findByLoginOrderByIdDesc(SpaceService.normalizeLogin(login)).stream()
                .map(AccountFlag::view).toList();
    }

    @Transactional
    public void forget(String login, String replacement) {
        String who = SpaceService.normalizeLogin(login);
        flags.deleteByLogin(who);
        flags.renameDecider(who, replacement);
    }
}
