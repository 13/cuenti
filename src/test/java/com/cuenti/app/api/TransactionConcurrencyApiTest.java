package com.cuenti.app.api;

import com.cuenti.app.repository.TransactionRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Idempotency-Key on create and If-Match on update/delete: what lets an
 * offline client resend a write safely and not overwrite someone else's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class TransactionConcurrencyApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TransactionRepository transactionRepository;

    private long accountId;

    @BeforeEach
    void setUp() throws Exception {
        String acct = mockMvc.perform(post("/api/accounts")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content("{\"accountName\":\"Concurrency test\",\"accountType\":\"BANK\",\"currency\":\"EUR\",\"startBalance\":1000,\"excludeFromSummary\":false,\"excludeFromReports\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accountId = ((Number) objectMapper.readValue(acct, Map.class).get("id")).longValue();
    }

    private String txJson(String payee, String amount) {
        return "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":" + amount
                + ",\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"" + payee + "\"}";
    }

    private Map<?, ?> json(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private MockHttpServletRequestBuilder createRequest(String payee, String key) {
        MockHttpServletRequestBuilder request = post("/api/transactions")
                .with(user("demo"))
                .contentType("application/json")
                .content(txJson(payee, "10.00"));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private Map<?, ?> create(String payee, String key) throws Exception {
        return json(mockMvc.perform(createRequest(payee, key)).andExpect(status().isOk()).andReturn());
    }

    private MockHttpServletRequestBuilder update(long id, String payee, String amount, String ifMatch) {
        MockHttpServletRequestBuilder request = put("/api/transactions/" + id)
                .with(user("demo"))
                .contentType("application/json")
                .content(txJson(payee, amount));
        return ifMatch == null ? request : request.header("If-Match", ifMatch);
    }

    private long countByPayee(String payee) {
        return transactionRepository.findAll().stream().filter(t -> payee.equals(t.getPayee())).count();
    }

    private static long id(Map<?, ?> body) {
        return ((Number) body.get("id")).longValue();
    }

    private static String quoted(Object version) {
        return "\"" + version + "\"";
    }

    @Test
    void repeatingACreateWithTheSameKeyReturnsTheFirstTransaction() throws Exception {
        String payee = "Idem-" + System.nanoTime();
        Map<?, ?> first = create(payee, "local-" + payee);

        MvcResult again = mockMvc.perform(createRequest(payee, "local-" + payee))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn();

        assertThat(id(json(again))).isEqualTo(id(first));
        assertThat(countByPayee(payee)).isEqualTo(1);
    }

    @Test
    void differentKeysCreateSeparateTransactions() throws Exception {
        String payee = "Idem-" + System.nanoTime();
        create(payee, "local-a-" + payee);
        create(payee, "local-b-" + payee);
        assertThat(countByPayee(payee)).isEqualTo(2);
    }

    @Test
    void withoutAKeyEveryCreateCreates() throws Exception {
        String payee = "NoKey-" + System.nanoTime();
        create(payee, null);
        create(payee, null);
        assertThat(countByPayee(payee)).isEqualTo(2);
    }

    @Test
    void anOverlongKeyIsRefused() throws Exception {
        mockMvc.perform(createRequest("Long-" + System.nanoTime(), "k".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void responsesCarryAVersion() throws Exception {
        Map<?, ?> created = create("Version-" + System.nanoTime(), null);
        assertThat((String) created.get("version")).isNotBlank();
    }

    @Test
    void anUpdateAgainstTheCurrentVersionSucceedsAndMovesTheVersion() throws Exception {
        String payee = "Upd-" + System.nanoTime();
        Map<?, ?> created = create(payee, null);
        Thread.sleep(5);

        Map<?, ?> updated = json(mockMvc.perform(update(id(created), payee, "12.00", quoted(created.get("version"))))
                .andExpect(status().isOk()).andReturn());

        assertThat(updated.get("version")).isNotEqualTo(created.get("version"));
    }

    @Test
    void anUpdateAgainstAnOlderVersionIsRefusedAndChangesNothing() throws Exception {
        String payee = "Stale-" + System.nanoTime();
        Map<?, ?> created = create(payee, null);
        String original = quoted(created.get("version"));
        Thread.sleep(5);
        mockMvc.perform(update(id(created), payee, "12.00", original)).andExpect(status().isOk());
        Thread.sleep(5);

        mockMvc.perform(update(id(created), payee, "13.00", original))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());

        assertThat(transactionRepository.findById(id(created)).orElseThrow().getAmount())
                .isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    void aDeleteAgainstAnOlderVersionIsRefusedAndKeepsTheRow() throws Exception {
        String payee = "StaleDel-" + System.nanoTime();
        Map<?, ?> created = create(payee, null);
        String original = quoted(created.get("version"));
        Thread.sleep(5);
        mockMvc.perform(update(id(created), payee, "12.00", original)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/transactions/" + id(created)).with(user("demo")).header("If-Match", original))
                .andExpect(status().isConflict());

        assertThat(transactionRepository.findById(id(created))).isPresent();
    }

    @Test
    void aDeleteAgainstTheCurrentVersionDeletes() throws Exception {
        Map<?, ?> created = create("Del-" + System.nanoTime(), null);

        mockMvc.perform(delete("/api/transactions/" + id(created))
                        .with(user("demo"))
                        .header("If-Match", quoted(created.get("version"))))
                .andExpect(status().isOk());

        assertThat(transactionRepository.findById(id(created))).isEmpty();
    }

    @Test
    void writesWithoutIfMatchKeepLastWriteWins() throws Exception {
        String payee = "Legacy-" + System.nanoTime();
        Map<?, ?> created = create(payee, null);
        Thread.sleep(5);
        mockMvc.perform(update(id(created), payee, "12.00", null)).andExpect(status().isOk());
        mockMvc.perform(update(id(created), payee, "13.00", null)).andExpect(status().isOk());
    }
}
