package com.cuenti.app.api;

import com.cuenti.app.model.Category;
import com.cuenti.app.model.User;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.UserService;
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
class BudgetApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryService categoryService;
    @Autowired UserService userService;

    private Long categoryId;

    @BeforeEach
    void setUp() {
        Category cat = new Category();
        cat.setName("Fuel-" + System.nanoTime());
        cat.setType(Category.CategoryType.EXPENSE);
        categoryId = categoryService.saveCategory(cat).getId();
    }

    // Note: class-level @WithMockUser only survives until the first request's filter
    // chain clears the SecurityContextHolder (stateless API chain), so every request
    // authenticates explicitly via the user() post-processor.
    private long createBudget(long catId, String limit) throws Exception {
        String body = mockMvc.perform(post("/api/budgets").with(user("demo"))
                        .contentType("application/json")
                        .content("{\"categoryId\":" + catId + ",\"monthlyLimit\":" + limit + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createAndListBudget() throws Exception {
        long id = createBudget(categoryId, "250.00");

        String body = mockMvc.perform(get("/api/budgets").with(user("demo")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(body);
        JsonNode created = null;
        for (JsonNode n : list) if (n.get("id").asLong() == id) created = n;
        assertThat(created).isNotNull();
        assertThat(created.get("categoryId").asLong()).isEqualTo(categoryId);
        assertThat(created.get("monthlyLimit").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(created.get("active").asBoolean()).isTrue();
    }

    @Test
    void duplicateCategoryRejected() throws Exception {
        createBudget(categoryId, "100");
        mockMvc.perform(post("/api/budgets").with(user("demo"))
                        .contentType("application/json")
                        .content("{\"categoryId\":" + categoryId + ",\"monthlyLimit\":50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCategoryIs404() throws Exception {
        mockMvc.perform(post("/api/budgets").with(user("demo"))
                        .contentType("application/json")
                        .content("{\"categoryId\":999999,\"monthlyLimit\":50}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAndDeleteBudget() throws Exception {
        long id = createBudget(categoryId, "100");

        mockMvc.perform(put("/api/budgets/" + id).with(user("demo"))
                        .contentType("application/json")
                        .content("{\"categoryId\":" + categoryId + ",\"monthlyLimit\":175.50,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyLimit").value(175.50))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/budgets/" + id).with(user("demo"))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/budgets/" + id).with(user("demo"))).andExpect(status().isNotFound());
    }

    @Test
    void progressReturnsSpentAndRemaining() throws Exception {
        createBudget(categoryId, "300");
        String body = mockMvc.perform(get("/api/budgets/progress").with(user("demo")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode mine = null;
        for (JsonNode n : objectMapper.readTree(body)) {
            if (n.get("categoryId").asLong() == categoryId) mine = n;
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("monthlyLimit").decimalValue()).isEqualByComparingTo("300");
        assertThat(mine.get("spent").decimalValue()).isEqualByComparingTo("0");
        assertThat(mine.get("remaining").decimalValue()).isEqualByComparingTo("300");
    }

    @Test
    void foreignBudgetInvisibleToOtherUser() throws Exception {
        long id = createBudget(categoryId, "100");
        userService.registerUser("intruder1", "intruder1@x.com", "password123", "In", "Truder");

        mockMvc.perform(put("/api/budgets/" + id).with(user("intruder1"))
                        .contentType("application/json")
                        .content("{\"categoryId\":" + categoryId + ",\"monthlyLimit\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/budgets/" + id).with(user("intruder1")))
                .andExpect(status().isNotFound());
    }
}
