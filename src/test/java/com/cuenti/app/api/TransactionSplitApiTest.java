package com.cuenti.app.api;

import com.cuenti.app.model.Category;
import com.cuenti.app.service.CategoryService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class TransactionSplitApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryService categoryService;

    private long accountId;
    private Long groceriesId;
    private Long householdId;

    @BeforeEach
    void setUp() throws Exception {
        Category groceries = new Category();
        groceries.setName("Groceries-" + System.nanoTime());
        groceries.setType(Category.CategoryType.EXPENSE);
        groceriesId = categoryService.saveCategory(groceries).getId();

        Category household = new Category();
        household.setName("Household-" + System.nanoTime());
        household.setType(Category.CategoryType.EXPENSE);
        householdId = categoryService.saveCategory(household).getId();

        String acct = mockMvc.perform(post("/api/accounts")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content("{\"accountName\":\"Split test\",\"accountType\":\"BANK\",\"currency\":\"EUR\",\"startBalance\":1000,\"excludeFromSummary\":false,\"excludeFromReports\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accountId = objectMapper.readTree(acct).get("id").asLong();
    }

    private String splitTxJson(String total, String split1, String split2) {
        return "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":" + total
                + ",\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Supermarket\""
                + ",\"splits\":["
                + "{\"categoryId\":" + groceriesId + ",\"amount\":" + split1 + ",\"memo\":\"food\"},"
                + "{\"categoryId\":" + householdId + ",\"amount\":" + split2 + ",\"memo\":\"cleaning\"}"
                + "]}";
    }

    @Test
    void createWithSplitsRoundTrips() throws Exception {
        String body = mockMvc.perform(post("/api/transactions")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(splitTxJson("50.00", "30.00", "20.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode tx = objectMapper.readTree(body);
        assertThat(tx.get("splits").get(0).get("categoryName").asString()).isNotEmpty();

        // GET returns splits too
        mockMvc.perform(get("/api/transactions").param("accountId", String.valueOf(accountId))
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].splits.length()").value(2));
    }

    @Test
    void mismatchedSplitSumIs400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(splitTxJson("50.00", "30.00", "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void updateReplacesSplits() throws Exception {
        String body = mockMvc.perform(post("/api/transactions")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(splitTxJson("50.00", "30.00", "20.00")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        String updated = "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":50.00,\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Supermarket\""
                + ",\"splits\":[{\"categoryId\":" + groceriesId + ",\"amount\":50.00}]}";
        mockMvc.perform(put("/api/transactions/" + id)
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits.length()").value(1))
                .andExpect(jsonPath("$.splits[0].amount").value(50.00));

        // Replacement must be persisted, not just echoed in the PUT response
        mockMvc.perform(get("/api/transactions").param("accountId", String.valueOf(accountId))
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].splits.length()").value(1))
                .andExpect(jsonPath("$[0].splits[0].amount").value(50.00));
    }

    @Test
    void putWithoutSplitsFieldPreservesExistingSplits() throws Exception {
        String body = mockMvc.perform(post("/api/transactions")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(splitTxJson("50.00", "30.00", "20.00")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        // splits key entirely absent: only rename the payee
        String updated = "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":50.00,\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Renamed\"}";
        mockMvc.perform(put("/api/transactions/" + id)
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payee").value("Renamed"))
                .andExpect(jsonPath("$.splits.length()").value(2));

        // Existing splits must survive in the store
        mockMvc.perform(get("/api/transactions").param("accountId", String.valueOf(accountId))
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].splits.length()").value(2));
    }

    @Test
    void splitWithUnknownCategoryIs400() throws Exception {
        String body = "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":50.00,\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Supermarket\""
                + ",\"splits\":[{\"categoryId\":999999999,\"amount\":50.00}]}";
        mockMvc.perform(post("/api/transactions")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void splitMissingAmountIs400() throws Exception {
        String body = "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":50.00,\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Supermarket\""
                + ",\"splits\":[{\"categoryId\":" + groceriesId + "}]}";
        mockMvc.perform(post("/api/transactions")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void putChangingAmountReversesOldBalanceNotNewOne() throws Exception {
        // startBalance 1000, POST expense 100 -> balance 900
        String body = mockMvc.perform(post("/api/transactions")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content("{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                                + ",\"amount\":100,\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Shop\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(get("/api/accounts/" + accountId).with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(900.00));

        // PUT changing amount to 50: correct reversal should leave balance at 950
        // (1000 - 50), not 800 (1000 - 100 - 50, the bug where reversal uses the
        // already-mutated new amount instead of the old one).
        String updated = "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":50,\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Shop\"}";
        mockMvc.perform(put("/api/transactions/" + id)
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.00));

        mockMvc.perform(get("/api/accounts/" + accountId).with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(950.00));

        // The new amount must actually be persisted, not just echoed in the PUT response.
        mockMvc.perform(get("/api/transactions").param("accountId", String.valueOf(accountId))
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(50.00));
    }

    @Test
    void putChangingAmountWithoutSplitsMismatchingExistingSplitSumIs400() throws Exception {
        String body = mockMvc.perform(post("/api/transactions")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(splitTxJson("50.00", "30.00", "20.00")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        // amount changed 50 -> 80 without resending splits: splits still sum to 50 on
        // an 80 tx, which must be rejected rather than silently persisted.
        String updated = "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":80.00,\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Supermarket\"}";
        mockMvc.perform(put("/api/transactions/" + id)
                        .with(user("demo"))
                        .contentType("application/json")
                        .content(updated))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());

        // Original amount/splits must be untouched after the rejected update.
        mockMvc.perform(get("/api/transactions").param("accountId", String.valueOf(accountId))
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(50.00))
                .andExpect(jsonPath("$[0].splits.length()").value(2));
    }

    @Test
    void noSplitsFieldStillWorks() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content("{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                                + ",\"amount\":10,\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"Kiosk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits").isArray())
                .andExpect(jsonPath("$.splits.length()").value(0));
    }
}
