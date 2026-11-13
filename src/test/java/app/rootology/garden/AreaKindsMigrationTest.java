package app.rootology.garden;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The migration of #85 on a database that already holds a container as a kind: after V31 the row is a pot,
 * and the check constraint takes the five new kinds and no old one.
 */
class AreaKindsMigrationTest {

    @Test
    void aContainerBecomesAPotAndTheFiveKindsAreAccepted() throws SQLException {
        try (PostgreSQLContainer postgres =
                     new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))) {
            postgres.start();
            String url = postgres.getJdbcUrl();
            String user = postgres.getUsername();
            String password = postgres.getPassword();
            Flyway.configure().dataSource(url, user, password).target(MigrationVersion.fromVersion("30")).load()
                    .migrate();

            try (Connection db = DriverManager.getConnection(url, user, password)) {
                execute(db, "insert into space (name, created_at) values ('Casa', now())");
                execute(db, "insert into garden (space_id, name, created_at) values (1, 'Orto', now())");
                execute(db, "insert into plot (garden_id, name, kind, soil) values (1, 'Vaso vecchio', 'CONTAINER',"
                        + " 'POTTING_MIX')");
            }

            Flyway.configure().dataSource(url, user, password).load().migrate();

            try (Connection db = DriverManager.getConnection(url, user, password)) {
                assertThat(kindOf(db, "Vaso vecchio")).as("the old container is a pot (#85)").isEqualTo("POT");
                for (String kind : new String[]{"GROUND", "RAISED_BED", "PLANTER", "POT", "GREENHOUSE"}) {
                    execute(db, "insert into plot (garden_id, name, kind, soil) values (1, '" + kind + "', '"
                            + kind + "', 'LOAMY')");
                }
                assertThat(kindOf(db, "GREENHOUSE")).isEqualTo("GREENHOUSE");
                assertThatThrownBy(() -> execute(db, "insert into plot (garden_id, name, kind, soil) values"
                        + " (1, 'Old', 'CONTAINER', 'LOAMY')"))
                        .as("the old kind is no longer a value of the constraint").isInstanceOf(SQLException.class);
            }
        }
    }

    private static void execute(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String kindOf(Connection db, String name) throws SQLException {
        try (Statement statement = db.createStatement();
             ResultSet rows = statement.executeQuery("select kind from plot where name = '" + name + "'")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
