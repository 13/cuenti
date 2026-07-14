package com.cuenti.app.api;

import com.cuenti.app.model.Category;
import com.cuenti.app.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the transaction search endpoint against a REAL PostgreSQL container.
 *
 * The H2-based suite cannot catch database-dialect bugs: the transactions
 * list was 500ing in production on Postgres because bind parameters in the
 * search query had no type cast ("could not determine data type of
 * parameter", SQLState 42P18), yet every H2 test passed because H2 infers
 * parameter types. This test exercises the exact filter paths (text search,
 * payee, tag, date range) that trigger that bug, so a regression fails here.
 *
 * Requires a Docker daemon; skipped automatically where Docker is absent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("pgtest")
@Testcontainers
@WithMockUser(username = "demo")
@EnabledIf("dockerAvailable")
class TransactionSearchPostgresTest {

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

    @Autowired MockMvc mockMvc;
    @Autowired CategoryService categoryService;

    @BeforeEach
    void seed() throws Exception {
        // A category and an account owned by the seeded demo user, plus a
        // couple of transactions to search over.
        Category cat = new Category();
        cat.setName("Groceries-" + System.nanoTime());
        cat.setType(Category.CategoryType.EXPENSE);
        var categoryId = categoryService.saveCategory(cat).getId();

        String accountJson = mockMvc.perform(post("/api/accounts").with(user("demo"))
                        .contentType("application/json")
                        .content("""
                            {"accountName":"PG Test","accountType":"BANK","currency":"EUR",
                             "startBalance":1000,"excludeFromSummary":false,"excludeFromReports":false}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long accountId = Long.parseLong(
                accountJson.replaceAll(".*\"id\":(\\d+).*", "$1"));

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/transactions").with(user("demo"))
                            .contentType("application/json")
                            .content("{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                                    + ",\"amount\":" + (i * 10)
                                    + ",\"transactionDate\":\"2026-0" + i + "-15T12:00:00\""
                                    + ",\"categoryId\":" + categoryId
                                    + ",\"payee\":\"Rewe " + i + "\",\"memo\":\"weekly shop\",\"tags\":\"food\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void unfilteredListReturnsRowsOnPostgres() throws Exception {
        // Without the parameter casts this 500s on Postgres (42P18).
        mockMvc.perform(get("/api/transactions").with(user("demo"))
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void everyFilterParamTypesCorrectlyOnPostgres() throws Exception {
        // Each of these paths binds a String/timestamp param that Postgres
        // must be able to type — the exact clauses the cast fix protects.
        mockMvc.perform(get("/api/transactions").with(user("demo"))
                        .param("search", "weekly").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));

        mockMvc.perform(get("/api/transactions").with(user("demo"))
                        .param("payee", "rewe 2").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/transactions").with(user("demo"))
                        .param("tag", "food").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));

        mockMvc.perform(get("/api/transactions").with(user("demo"))
                        .param("type", "EXPENSE")
                        .param("start", "2026-02-01").param("end", "2026-03-31")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }
}
