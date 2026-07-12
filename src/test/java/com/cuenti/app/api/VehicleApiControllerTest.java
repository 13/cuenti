package com.cuenti.app.api;

import com.cuenti.app.model.Category;
import com.cuenti.app.service.CategoryService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
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
class VehicleApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryService categoryService;

    private Long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        Category cat = new Category();
        cat.setName("Car-" + System.nanoTime());
        cat.setType(Category.CategoryType.EXPENSE);
        categoryId = categoryService.saveCategory(cat).getId();

        // an account to spend from
        String acct = mockMvc.perform(post("/api/accounts").with(user("demo"))
                        .contentType("application/json")
                        .content("{\"accountName\":\"Fuel Card\",\"accountType\":\"BANK\",\"currency\":\"EUR\",\"startBalance\":\"1000.00\",\"excludeFromSummary\":false,\"excludeFromReports\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long accountId = objectMapper.readTree(acct).get("id").asLong();

        mockMvc.perform(post("/api/transactions").with(user("demo"))
                        .contentType("application/json")
                        .content("{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                                + ",\"amount\":60,\"transactionDate\":\"2026-02-01T10:00:00\""
                                + ",\"categoryId\":" + categoryId
                                + ",\"payee\":\"Aral\",\"memo\":\"d=1000 l=40 full\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/transactions").with(user("demo"))
                        .contentType("application/json")
                        .content("{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                                + ",\"amount\":55,\"transactionDate\":\"2026-03-01T10:00:00\""
                                + ",\"categoryId\":" + categoryId
                                + ",\"payee\":\"Aral\",\"memo\":\"d=1500 l=35 full\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void reportComputesConsumption() throws Exception {
        String body = mockMvc.perform(get("/api/vehicles/report").with(user("demo"))
                        .param("categoryId", categoryId.toString())
                        .param("start", "2026-01-01")
                        .param("end", "2026-12-31"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode report = objectMapper.readTree(body);
        assertThat(report.get("entries").size()).isEqualTo(2);
        assertThat(report.get("totalLiters").decimalValue()).isEqualByComparingTo("75");
        assertThat(report.get("totalDistance").decimalValue()).isEqualByComparingTo("500");
        assertThat(report.get("avgConsumption").decimalValue()).isEqualByComparingTo("7.00");
        // newest first
        assertThat(report.get("entries").get(0).get("odometer").decimalValue()).isEqualByComparingTo("1500");
    }

    @Test
    void missingCategoryWithoutDefaultIs400() throws Exception {
        mockMvc.perform(get("/api/vehicles/report").with(user("demo")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultVehicleCategoryPreferenceUsed() throws Exception {
        mockMvc.perform(put("/api/user/preferences").with(user("demo"))
                        .contentType("application/json")
                        .content("{\"defaultVehicleCategoryId\":" + categoryId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultVehicleCategoryId").value(categoryId.intValue()));

        mockMvc.perform(get("/api/vehicles/report").with(user("demo"))
                        .param("start", "2026-01-01").param("end", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2));
    }
}
