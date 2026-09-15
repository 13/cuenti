package com.cuenti.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exchange rates are cached, including failed lookups, so converting many
 * amounts does not issue one HTTP call per amount.
 */
class ExchangeRateServiceTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    private final ExchangeRateService service = new ExchangeRateService(restTemplate);

    @Test
    void successfulRate_isFetchedOnce() {
        server.expect(ExpectedCount.once(), requestTo(containsString("EURUSD=X")))
                .andRespond(withSuccess("{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":1.5}}]}}",
                        MediaType.APPLICATION_JSON));

        for (int i = 0; i < 100; i++) {
            assertThat(service.convert(new BigDecimal("10.00"), "EUR", "USD")).isEqualByComparingTo("15.00");
        }
        server.verify();
    }

    @Test
    void failedRate_isNotRetriedForEveryConversion() {
        server.expect(ExpectedCount.once(), requestTo(containsString("EURXXX=X"))).andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(containsString("XXXEUR=X"))).andRespond(withServerError());

        for (int i = 0; i < 100; i++) {
            assertThat(service.convert(new BigDecimal("10.00"), "EUR", "XXX")).isEqualByComparingTo("10.00");
        }
        server.verify();
    }
}
