package com.cuenti.app.api;

import com.cuenti.app.model.Account;
import com.cuenti.app.service.AccountService;
import com.cuenti.app.service.UserService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API clients cannot create transactions that miss the account their type books on:
 * an income sent with only a source account lands in that account, and a transaction
 * without any account is a 400 instead of an orphan row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class TransactionAccountSideApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountService accountService;
    @Autowired UserService userService;

    @Test
    void incomeWithSourceOnly_isBookedIntoThatAccount() throws Exception {
        Account account = accountService.getAccountsByUser(userService.findByUsername("demo")).get(0);
        String body = mockMvc.perform(post("/api/transactions").with(user("demo"))
                        .contentType("application/json")
                        .content("{\"type\":\"INCOME\",\"fromAccountId\":" + account.getId()
                                + ",\"amount\":12.34,\"transactionDate\":\"2026-09-01T00:00:00\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("toAccountId").asLong()).isEqualTo(account.getId());
        assertThat(json.get("fromAccountId").isNull()).isTrue();
    }

    @Test
    void transactionWithoutAccount_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/transactions").with(user("demo"))
                        .contentType("application/json")
                        .content("{\"type\":\"EXPENSE\",\"amount\":5.00,\"transactionDate\":\"2026-09-01T00:00:00\"}"))
                .andExpect(status().isBadRequest());
    }
}
