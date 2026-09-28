package com.cuenti.app.api;

import com.cuenti.app.repository.AccountRepository;
import com.cuenti.app.repository.TransactionRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * POST /api/transactions/batch: several queued writes in one round trip,
 * each with the single-item endpoint's rules and in its own database
 * transaction.
 *
 * <p>Deliberately not {@code @Transactional}: under a test transaction every
 * operation would join the one transaction, and whether one refused operation
 * rolls back the others -- the point of the endpoint -- could not be seen.
 * What each test creates is removed afterwards instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class TransactionBatchApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TransactionRepository transactionRepository;
    @Autowired AccountRepository accountRepository;

    private long accountId;

    @BeforeEach
    void setUp() throws Exception {
        String acct = mockMvc.perform(post("/api/accounts")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content("{\"accountName\":\"Batch test " + System.nanoTime()
                                + "\",\"accountType\":\"BANK\",\"currency\":\"EUR\",\"startBalance\":1000,"
                                + "\"excludeFromSummary\":false,\"excludeFromReports\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accountId = ((Number) objectMapper.readValue(acct, Map.class).get("id")).longValue();
    }

    @AfterEach
    void tearDown() {
        transactionRepository.findAll().stream()
                .filter(t -> (t.getFromAccount() != null && t.getFromAccount().getId() == accountId)
                        || (t.getToAccount() != null && t.getToAccount().getId() == accountId))
                .forEach(transactionRepository::delete);
        accountRepository.deleteById(accountId);
    }

    private String tx(String payee, String amount) {
        return "{\"type\":\"EXPENSE\",\"fromAccountId\":" + accountId
                + ",\"amount\":" + amount
                + ",\"transactionDate\":\"2026-05-01T12:00:00\",\"payee\":\"" + payee + "\"}";
    }

    private static String op(String clientId, String op, Long id, String version, String key, String tx) {
        StringBuilder b = new StringBuilder("{\"clientId\":\"").append(clientId)
                .append("\",\"op\":\"").append(op).append('"');
        if (id != null) b.append(",\"id\":").append(id);
        if (version != null) b.append(",\"version\":\"").append(version).append('"');
        if (key != null) b.append(",\"idempotencyKey\":\"").append(key).append('"');
        if (tx != null) b.append(",\"transaction\":").append(tx);
        return b.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> batch(String... operations) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/transactions/batch")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content("{\"operations\":[" + String.join(",", operations) + "]}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (List<Map<String, Object>>) body.get("results");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> transaction(Map<String, Object> result) {
        return (Map<String, Object>) result.get("transaction");
    }

    private static long id(Map<String, Object> result) {
        return ((Number) transaction(result).get("id")).longValue();
    }

    private long countByPayee(String payee) {
        return transactionRepository.findAll().stream().filter(t -> payee.equals(t.getPayee())).count();
    }

    @Test
    void eachOperationGetsWhatItsOwnRequestWouldHaveInRequestOrder() throws Exception {
        String payee = "Mixed-" + System.nanoTime();
        Map<String, Object> created = batch(op("c1", "CREATE", null, null, null, tx(payee, "10.00"))).get(0);

        List<Map<String, Object>> results = batch(
                op("a", "UPDATE", id(created), "not-the-version", null, tx(payee, "11.00")),
                op("b", "DELETE", 999_999_999L, null, null, null),
                op("c", "EXPLODE", null, null, null, null),
                op("d", "CREATE", null, null, null, tx(payee, "5.00")));

        assertThat(results).extracting(r -> r.get("clientId")).containsExactly("a", "b", "c", "d");
        assertThat(results).extracting(r -> r.get("status")).containsExactly(409, 404, 400, 200);
        assertThat(results.get(0).get("error")).isNotNull();
        assertThat(transaction(results.get(3)).get("version")).isNotNull();
    }

    @Test
    void aRefusedOperationRollsBackNothingElse() throws Exception {
        String payee = "Isolated-" + System.nanoTime();

        List<Map<String, Object>> results = batch(
                op("first", "CREATE", null, null, null, tx(payee, "10.00")),
                // Negative amounts are refused inside the service's own transaction.
                op("bad", "CREATE", null, null, null, tx(payee, "-1.00")),
                op("last", "CREATE", null, null, null, tx(payee, "12.00")));

        assertThat(results).extracting(r -> r.get("status")).containsExactly(200, 400, 200);
        assertThat(countByPayee(payee)).isEqualTo(2);
    }

    @Test
    void aResentCreateIsAnsweredWithTheFirstOne() throws Exception {
        String payee = "Replay-" + System.nanoTime();
        String create = op("c", "CREATE", null, null, "local-" + payee, tx(payee, "10.00"));

        Map<String, Object> first = batch(create).get(0);
        Map<String, Object> again = batch(create).get(0);

        assertThat(again.get("status")).isEqualTo(200);
        assertThat(again.get("replayed")).isEqualTo(true);
        assertThat(id(again)).isEqualTo(id(first));
        assertThat(countByPayee(payee)).isEqualTo(1);
    }

    @Test
    void aResentUpdateIsNotRefusedAsStaleByTheChangeItMadeItself() throws Exception {
        String payee = "UpdReplay-" + System.nanoTime();
        Map<String, Object> created = batch(op("c", "CREATE", null, null, null, tx(payee, "10.00"))).get(0);
        String version = (String) transaction(created).get("version");
        Thread.sleep(5);
        String update = op("u", "UPDATE", id(created), version, "u:" + payee, tx(payee, "12.00"));

        Map<String, Object> first = batch(update).get(0);
        Map<String, Object> again = batch(update).get(0);

        assertThat(first.get("status")).isEqualTo(200);
        assertThat(again.get("status")).isEqualTo(200);
        assertThat(again.get("replayed")).isEqualTo(true);
        assertThat(transactionRepository.findById(id(created)).orElseThrow().getAmount())
                .isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    void aDeleteAgainstTheCurrentVersionDeletes() throws Exception {
        String payee = "Del-" + System.nanoTime();
        Map<String, Object> created = batch(op("c", "CREATE", null, null, null, tx(payee, "10.00"))).get(0);

        Map<String, Object> deleted = batch(op("d", "DELETE", id(created),
                (String) transaction(created).get("version"), null, null)).get(0);

        assertThat(deleted.get("status")).isEqualTo(200);
        assertThat(transactionRepository.findById(id(created))).isEmpty();
    }

    @Test
    void moreThanTheLimitIsRefusedWhole() throws Exception {
        String one = op("x", "DELETE", 1L, null, null, null);
        String many = String.join(",", java.util.Collections.nCopies(TransactionApiController.MAX_BATCH + 1, one));

        mockMvc.perform(post("/api/transactions/batch")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content("{\"operations\":[" + many + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theSignInScreenCanAskWhetherRegistrationIsOpenWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/api/auth/settings").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").isBoolean());
    }
}
