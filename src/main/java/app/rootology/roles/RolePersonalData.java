package app.rootology.roles;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * App roles: export and account deletion (#40, docs/privacy.md). The history of grants is kept for
 * accountability, with the login replaced.
 */
@Component
public class RolePersonalData {

    private final JdbcClient jdbc;

    RolePersonalData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> export(String login) {
        return jdbc.sql("""
                        select role, granted_by, granted_at, revoked_by, revoked_at
                        from app_role_grant where github_login = :login order by id""")
                .param("login", login).query().listOfRows();
    }

    /** True if the person is the only active admin: an app always keeps one. */
    public boolean isLastAdmin(String login) {
        return jdbc.sql("""
                        select count(*) filter (where github_login = :login) = 1 and count(*) = 1
                        from app_role_grant where role = 'ADMIN' and revoked_at is null""")
                .param("login", login).query(Boolean.class).single();
    }

    public void forget(String login, String replacement, Instant now) {
        jdbc.sql("update app_role_grant set revoked_at = :now, revoked_by = :r where github_login = :login and revoked_at is null")
                .param("now", Timestamp.from(now)).param("r", replacement).param("login", login).update();
        jdbc.sql("update app_role_grant set github_login = :r where github_login = :login").param("r", replacement).param("login", login).update();
        jdbc.sql("update app_role_grant set granted_by = :r where granted_by = :login").param("r", replacement).param("login", login).update();
        jdbc.sql("update app_role_grant set revoked_by = :r where revoked_by = :login").param("r", replacement).param("login", login).update();
    }
}
