package com.cuenti.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds the schema the way production does -- Flyway migrations on a real
 * PostgreSQL, then Hibernate {@code ddl-auto=validate} -- and checks the
 * entities accept it.
 *
 * Every other test lets Hibernate generate the schema, so a migration that
 * forgets a column, or declares one with the wrong type, passes the whole
 * suite and fails only when production starts. Reaching the assertions here
 * means validation already succeeded.
 *
 * Requires a Docker daemon; skipped automatically where Docker is absent.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("pgtest")
@Testcontainers
@EnabledIf("dockerAvailable")
class FlywaySchemaPostgresTest {

    /** Skip (rather than fail) on machines without a usable Docker daemon. */
    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationsProduceTheSchemaTheEntitiesMap() {
        String latest = jdbc.queryForObject(
                "select version from flyway_schema_history where success order by installed_rank desc limit 1",
                String.class);
        assertThat(latest).isEqualTo("7");

        assertThat(jdbc.queryForObject(
                "select count(*) from pg_indexes "
                        + "where tablename in ('transactions', 'transaction_splits') and indexname like 'idx_%'",
                Integer.class)).isEqualTo(5);

        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_name = 'transactions' and column_name = 'updated_at'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.table_constraints "
                        + "where table_name = 'idempotency_keys' and constraint_type = 'UNIQUE'",
                Integer.class)).isEqualTo(1);
    }
}
