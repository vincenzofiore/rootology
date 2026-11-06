package io.github.vincenzofiore.rootology.spaces;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A person's data in this module, for export, account deletion and retention (#40, docs/privacy.md).
 * Called by the privacy module only; logins are normalised by the caller.
 */
@Component
public class SpacePersonalData {

    /** What deleting an account does to the person's spaces. */
    public record DeletionPlan(Set<Long> spacesToDelete, List<String> spacesToDeleteNames, List<String> blockingSpaces) {
    }

    private final JdbcClient jdbc;

    SpacePersonalData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The personal space and the spaces where the person is the only member are deleted with the account. A
     * space where they are the only owner but others remain blocks it: they hand it over first.
     */
    public DeletionPlan plan(String login) {
        Set<Long> delete = new TreeSet<>();
        List<String> deleteNames = new ArrayList<>();
        List<String> blocking = new ArrayList<>();
        jdbc.sql("""
                        select s.id, s.name, s.personal_of = u.id as personal, m.role,
                               (select count(*) from space_member a where a.space_id = s.id) as members,
                               (select count(*) from space_member o where o.space_id = s.id and o.role = 'OWNER') as owners
                        from space_member m join space s on s.id = m.space_id join app_user u on u.id = m.user_id
                        where u.login = :login order by s.id""")
                .param("login", login)
                .query((rs, n) -> {
                    long id = rs.getLong("id");
                    String name = rs.getString("name");
                    boolean personal = rs.getBoolean("personal");
                    long members = rs.getLong("members");
                    if (personal || members == 1) {
                        delete.add(id);
                        deleteNames.add(name);
                    } else if ("OWNER".equals(rs.getString("role")) && rs.getLong("owners") == 1) {
                        blocking.add(name);
                    }
                    return id;
                }).list();
        return new DeletionPlan(delete, deleteNames, blocking);
    }

    /** The spaces the person belongs to, for exporting what is in them. */
    public List<Long> spacesOf(String login) {
        return jdbc.sql("""
                        select m.space_id from space_member m join app_user u on u.id = m.user_id
                        where u.login = :login order by m.space_id""")
                .param("login", login).query(Long.class).list();
    }

    public Map<String, Object> export(String login) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("account", jdbc.sql("select login, created_at, first_login_at from app_user where login = :login")
                .param("login", login).query().listOfRows());
        // The GitHub account behind this person, by its id and the address GitHub verified (#28).
        data.put("identities", jdbc.sql("""
                        select i.provider, i.subject, i.email, i.linked_at
                        from user_identity i join app_user u on u.id = i.user_id
                        where u.login = :login order by i.id""")
                .param("login", login).query().listOfRows());
        data.put("spaces", jdbc.sql("""
                        select s.id, s.name, m.role, m.joined_at, s.personal_of = u.id as personal
                        from space_member m join space s on s.id = m.space_id join app_user u on u.id = m.user_id
                        where u.login = :login order by s.id""")
                .param("login", login).query().listOfRows());
        // Only what the person did; the invited person's address is theirs, not part of this export.
        data.put("invitationsCreated", jdbc.sql("""
                        select space_id, role, invited_login, created_at, expires_at, accepted_by, accepted_at, revoked_at
                        from invitation where created_by = :login order by id""")
                .param("login", login).query().listOfRows());
        data.put("invitationsAccepted", jdbc.sql("""
                        select space_id, role, created_by, accepted_at from invitation where accepted_by = :login order by id""")
                .param("login", login).query().listOfRows());
        return data;
    }

    /**
     * Runs last, after every other module has let go of the spaces: their invitations, the person's open
     * invitations and memberships, the spaces themselves and the account. Elsewhere the login is replaced.
     */
    public void forget(String login, Set<Long> spaces, String replacement) {
        if (!spaces.isEmpty()) {
            jdbc.sql("delete from invitation where space_id in (:spaces)").param("spaces", spaces).update();
        }
        jdbc.sql("delete from invitation where created_by = :login and accepted_at is null and revoked_at is null")
                .param("login", login).update();
        jdbc.sql("update invitation set created_by = :r where created_by = :login").param("r", replacement).param("login", login).update();
        jdbc.sql("update invitation set accepted_by = :r where accepted_by = :login").param("r", replacement).param("login", login).update();
        jdbc.sql("update invitation set invited_login = null where invited_login = :login").param("login", login).update();
        if (!spaces.isEmpty()) {
            jdbc.sql("delete from space_member where space_id in (:spaces)").param("spaces", spaces).update();
        }
        jdbc.sql("delete from space_member where user_id = (select id from app_user where login = :login)")
                .param("login", login).update();
        jdbc.sql("update space set personal_of = null where personal_of = (select id from app_user where login = :login)")
                .param("login", login).update();
        if (!spaces.isEmpty()) {
            jdbc.sql("delete from space where id in (:spaces)").param("spaces", spaces).update();
        }
        jdbc.sql("delete from app_user where login = :login").param("login", login).update();
    }

    /** Invitations accepted, revoked or expired before the cutoff. */
    public int purgeInvitations(Instant cutoff) {
        return jdbc.sql("""
                        delete from invitation where accepted_at < :cutoff or revoked_at < :cutoff
                            or (accepted_at is null and revoked_at is null and expires_at < :cutoff)""")
                .param("cutoff", java.sql.Timestamp.from(cutoff)).update();
    }
}
