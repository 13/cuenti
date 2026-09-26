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
        assertThat(latest).isEqualTo("10");

        assertThat(jdbc.queryForObject(
                "select count(*) from pg_indexes "
                        + "where schemaname = 'public' and tablename in ('transactions', 'transaction_splits') and indexname like 'idx_%'",
                Integer.class)).isEqualTo(6);

        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'transactions' and column_name = 'updated_at'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and ((table_name = 'users' and column_name in ('scheduled_badge_days', 'scheduled_horizon_days')) "
                        + "or (table_name = 'transactions' and column_name = 'scheduled_transaction_id'))",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.table_constraints "
                        + "where table_schema = 'public' and table_name = 'idempotency_keys' and constraint_type = 'UNIQUE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' and tablename = 'tags' and indexname = 'uq_tags_user_name'",
                Integer.class)).isEqualTo(1);
    }

    /**
     * V9 repairs data written before the fix: income posted with its account in
     * from_account_id gets moved to to_account_id and credited, duplicate tag rows
     * merge, and tag strings lose case-insensitive duplicates. Runs V1..V8 in a
     * scratch schema, seeds broken rows, then applies V9.
     */
    @Test
    void v9_repairsIncomeAccountsAndDuplicateTags() {
        String schema = "v9check";
        org.flywaydb.core.Flyway before = org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).target("8").load();
        before.migrate();

        String s = schema + ".";
        jdbc.update("insert into " + s + "users (id, username, email, password, first_name, last_name, "
                + "enabled, api_enabled, dark_mode, created_at) values (1, 'v9', 'v9@x', 'x', 'V', 'Nine', true, false, false, now())");
        jdbc.update("insert into " + s + "accounts (id, user_id, account_name, account_number, account_type, currency, "
                + "balance, start_balance, sort_order, exclude_from_reports, exclude_from_summary, created_at) "
                + "values (1, 1, 'Giro', 'V9-1', 'CURRENT', 'EUR', 100.00, 0, 0, false, false, now())");
        jdbc.update("insert into " + s + "transactions (id, type, amount, from_account_id, to_account_id, transaction_date, "
                + "sort_order, status, tags) values (1, 'INCOME', 50.00, 1, null, now(), 0, 'COMPLETED', 'Gehalt, gehalt,,Fix')");
        jdbc.update("insert into " + s + "scheduled_transactions (id, user_id, type, amount, from_account_id, "
                + "next_occurrence, recurrence_pattern, enabled, tags) "
                + "values (1, 1, 'INCOME', 50.00, 1, now(), 'MONTHLY', true, 'Gehalt,Gehalt')");
        jdbc.update("insert into " + s + "tags (id, user_id, name) values (1, 1, 'Gehalt'), (2, 1, 'gehalt '), (3, 1, 'Fix')");

        org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).load().migrate();

        assertThat(jdbc.queryForObject("select balance from " + s + "accounts where id = 1", java.math.BigDecimal.class))
                .isEqualByComparingTo("150.00");
        assertThat(jdbc.queryForObject("select to_account_id from " + s + "transactions where id = 1", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select from_account_id from " + s + "transactions where id = 1", Long.class)).isNull();
        assertThat(jdbc.queryForObject("select tags from " + s + "transactions where id = 1", String.class)).isEqualTo("Gehalt,Fix");
        assertThat(jdbc.queryForObject("select to_account_id from " + s + "scheduled_transactions where id = 1", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select tags from " + s + "scheduled_transactions where id = 1", String.class)).isEqualTo("Gehalt");
        assertThat(jdbc.queryForList("select name from " + s + "tags order by id", String.class)).containsExactly("Gehalt", "Fix");
    }
}
