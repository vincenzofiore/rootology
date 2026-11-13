package app.rootology.access;

import app.rootology.spaces.IdentityService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A person's invitation requests (#40, docs/privacy.md). Once a person is admitted their approved request is
 * gone, so an export normally finds nothing; what is found is keyed by the identities they signed in with.
 * Deleting the account deletes any request left of those identities.
 */
@Component
public class AccessPersonalData {

    private final AccessRequestService requests;
    private final IdentityService identities;

    AccessPersonalData(AccessRequestService requests, IdentityService identities) {
        this.requests = requests;
        this.identities = identities;
    }

    public List<AccessRequestView> export(String login) {
        List<AccessRequestView> mine = new ArrayList<>();
        identities.accountsOf(login).forEach(account ->
                mine.addAll(requests.of(account.provider(), account.subject())));
        return mine;
    }

    public void forget(String login) {
        identities.accountsOf(login).forEach(account -> requests.forget(account.provider(), account.subject()));
    }
}
