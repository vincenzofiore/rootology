package app.rootology;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres, Neo4j and Mailpit for tests that start a Spring context (needs Docker). Same images as
 * compose.yaml. Every email sent in a test goes to Mailpit; its API is at `rootology.test.mailpit-api`.
 *
 * <p>The three containers are static singletons (#98): Spring builds a context for each distinct test
 * configuration, and every context gets the same running containers instead of its own (#68). They are
 * deliberately not Spring beans: a container bean is stopped when its context closes, and a context that
 * failed to load (a database that is not reachable yet, for example) would stop the containers of every
 * other context with it. Testcontainers' Ryuk removes them when the test JVM exits.
 *
 * <p>The consequence is that the data is shared too: a test must not depend on an empty database or an
 * untouched graph (see docs/testing.md).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Every address of the containers is passed as properties: Boot's service connections need beans. */
    @Bean
    DynamicPropertyRegistrar containerProperties() {
        return registry -> {
            PostgreSQLContainer postgres = Containers.postgres;
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.neo4j.uri", Containers.neo4j::getBoltUrl);
            registry.add("spring.mail.host", Containers.mailpit::getHost);
            registry.add("spring.mail.port", () -> Containers.mailpit.getMappedPort(1025));
            registry.add("rootology.test.mailpit-api", () -> "http://" + Containers.mailpit.getHost() + ":"
                    + Containers.mailpit.getMappedPort(8025));
        };
    }

    /**
     * The containers of the whole test JVM, started once when the first context asks for their
     * addresses. They stay up until the JVM exits (Ryuk removes them after that).
     */
    private static final class Containers {

        private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
                DockerImageName.parse("postgres:17-alpine"))
                // Every cached context keeps its own connection pool (see the pool size in
                // src/test/resources/config/application.properties): 100 connections are too few.
                .withCommand("postgres", "-c", "max_connections=300");

        /** A capped heap and page cache: Neo4j used to be OOMKilled with many contexts alive (#68). */
        private static final Neo4jContainer neo4j = new Neo4jContainer(DockerImageName.parse("neo4j:2026.10-community"))
                .withoutAuthentication()
                .withEnv("NEO4J_server_memory_heap_initial__size", "256m")
                .withEnv("NEO4J_server_memory_heap_max__size", "512m")
                .withEnv("NEO4J_server_memory_pagecache_size", "128m");

        private static final GenericContainer<?> mailpit = new GenericContainer<>(
                DockerImageName.parse("axllent/mailpit:v1.33.0"))
                .withExposedPorts(1025, 8025);

        static {
            postgres.start();
            neo4j.start();
            mailpit.start();
        }
    }
}
