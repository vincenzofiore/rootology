package io.github.vincenzofiore.rootology.roles;

import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Who holds which app role, with the history. The roles read here become Spring Security authorities at
 * sign-in; the methods for admins are protected with method security. The last admin cannot be removed.
 * adminLogin parameters record who made a change; the authority decides whether they may.
 */
@Service
public class RoleService {

    private final RoleGrantRepository grants;
    private final SpaceService spaces;

    RoleService(RoleGrantRepository grants, SpaceService spaces) {
        this.grants = grants;
        this.spaces = spaces;
    }

    /** The active roles of a person, read at sign-in to become their authorities. */
    @Transactional(readOnly = true)
    public Set<AppRole> rolesOf(String login) {
        Set<AppRole> roles = EnumSet.noneOf(AppRole.class);
        if (login != null) {
            String who = SpaceService.normalizeLogin(login);
            for (AppRole role : AppRole.values()) {
                if (grants.existsByGithubLoginAndRoleAndRevokedAtIsNull(who, role)) {
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    /** Everyone the app knows, with their active roles, by login. */
    @PreAuthorize("hasRole(\'ADMIN\')")
    @Transactional(readOnly = true)
    public Map<String, Set<AppRole>> people(String admin) {
        Map<String, Set<AppRole>> people = new TreeMap<>();
        spaces.people().forEach(p -> people.put(p, EnumSet.noneOf(AppRole.class)));
        grants.findByRevokedAtIsNull().forEach(g ->
                people.computeIfAbsent(g.getGithubLogin(), k -> EnumSet.noneOf(AppRole.class)).add(g.getRole()));
        return people;
    }

    @PreAuthorize("hasRole(\'ADMIN\')")
    @Transactional
    public void grant(String admin, String login, AppRole role) {
        String who = SpaceService.normalizeLogin(login);
        if (!spaces.people().contains(who)) {
            throw new IllegalArgumentException(who + " has never signed in or been added to a space");
        }
        if (grants.existsByGithubLoginAndRoleAndRevokedAtIsNull(who, role)) {
            throw new IllegalStateException(who + " is already " + role);
        }
        grants.save(new RoleGrant(who, role, SpaceService.normalizeLogin(admin), Instant.now()));
    }

    @PreAuthorize("hasRole(\'ADMIN\')")
    @Transactional
    public void revoke(String admin, String login, AppRole role) {
        String who = SpaceService.normalizeLogin(login);
        RoleGrant grant = grants.findByGithubLoginAndRoleAndRevokedAtIsNull(who, role)
                .orElseThrow(() -> new IllegalStateException(who + " is not " + role));
        if (role == AppRole.ADMIN && grants.countByRoleAndRevokedAtIsNull(AppRole.ADMIN) == 1) {
            throw new IllegalStateException("There must always be at least one admin");
        }
        grant.revoke(SpaceService.normalizeLogin(admin), Instant.now());
    }

    /** Every grant and revocation, newest first. */
    @PreAuthorize("hasRole(\'ADMIN\')")
    @Transactional(readOnly = true)
    public List<RoleGrantView> history(String admin) {
        return grants.findAllByOrderByIdDesc().stream().map(RoleGrant::view).toList();
    }
}
