package app.rootology;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres, Neo4j and Mailpit for tests that start a Spring context (needs Docker). Same images as
 * compose.yaml. Every email sent in a test goes to Mailpit; its API is at `rootology.test.mailpit-api`.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection
    Neo4jContainer neo4j() {
        return new Neo4jContainer(DockerImageName.parse("neo4j:2026.10-community")).withoutAuthentication();
    }

    @Bean
    GenericContainer<?> mailpit() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.33.0")).withExposedPorts(1025, 8025);
    }

    /** Spring Boot has no service connection for Mailpit: its address is passed as properties. */
    @Bean
    DynamicPropertyRegistrar mailpitProperties(GenericContainer<?> mailpit) {
        return registry -> {
            registry.add("spring.mail.host", mailpit::getHost);
            registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
            registry.add("rootology.test.mailpit-api", () -> "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025));
        };
    }
}
