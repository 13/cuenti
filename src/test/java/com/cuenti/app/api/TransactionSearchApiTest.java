package com.cuenti.app.api;

import com.cuenti.app.model.Category;
import com.cuenti.app.service.CategoryService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class TransactionSearchApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryService categoryService;

    private long accountId;
    private Long foodCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        Category food = new Category();
        food.setName("Food-" + System.nanoTime());
        food.setType(Category.CategoryType.EXPENSE);
        foodCategoryId = categoryService.saveCategory(food).getId();

        String acct = mockMvc.perform(post("/api/accounts")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content("{\"accountName\":\"Search test\",\"accountType\":\"BANK\",\"currency\":\"EUR\",\"startBalance\":1000,\"excludeFromSummary\":false,\"excludeFromReports\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accountId = objectMapper.readTree(acct).get("id").asLong();

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/transactions")
                            .with(user("demo"))
                            .contentType("application/json")
                            .content("{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                                    + ",\"amount\":" + (i * 10)
                                    + ",\"transactionDate\":\"2026-0" + i + "-15T12:00:00\""
                                    + ",\"categoryId\":" + foodCategoryId
                                    + ",\"payee\":\"Rewe " + i + "\",\"memo\":\"weekly shop\",\"tags\":\"food\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void noParamsReturnsPlainArray() throws Exception {
        mockMvc.perform(get("/api/transactions").param("accountId", String.valueOf(accountId))
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void paginationWrapsResponse() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(accountId))
                        .param("page", "0").param("size", "2")
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                // default sort: newest first
                .andExpect(jsonPath("$.content[0].payee").value("Rewe 5"));
    }

    @Test
    void dateRangeFilterWorks() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(accountId))
                        .param("start", "2026-02-01").param("end", "2026-03-31")
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)); // Feb + Mar
    }

    @Test
    void payeeAndSearchFiltersWork() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(accountId))
                        .param("payee", "rewe 3")
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(accountId))
                        .param("search", "weekly")
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void categoryTypeAndTagFiltersWork() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("categoryId", foodCategoryId.toString())
                        .param("type", "EXPENSE")
                        .param("tag", "food")
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void sortByAmountAscending() throws Exception {
        // smallest amount (10) belongs to "Rewe 1"
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", String.valueOf(accountId))
                        .param("sort", "amount,asc")
                        .param("page", "0").param("size", "1")
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].payee").value("Rewe 1"));
    }

    @Test
    void invalidSortFieldIs400() throws Exception {
        mockMvc.perform(get("/api/transactions").param("sort", "payee;DROP TABLE,asc")
                        .param("page", "0").param("size", "10")
                        .with(user("demo")))
                .andExpect(status().isBadRequest());
    }
}
