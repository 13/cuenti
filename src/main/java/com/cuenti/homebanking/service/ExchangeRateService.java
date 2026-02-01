package com.cuenti.homebanking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, BigDecimal> rateCache = new HashMap<>();

    public BigDecimal getExchangeRate(String from, String to) {
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }

        String pair = from + to;
        if (rateCache.containsKey(pair)) {
            return rateCache.get(pair);
        }

        BigDecimal rate = fetchRate(from, to);
        if (rate != null) {
            rateCache.put(pair, rate);
            return rate;
        }

        // Try inverse if direct failed
        BigDecimal inverseRate = fetchRate(to, from);
        if (inverseRate != null && inverseRate.compareTo(BigDecimal.ZERO) != 0) {
            rate = BigDecimal.ONE.divide(inverseRate, 10, RoundingMode.HALF_UP);
            rateCache.put(pair, rate);
            return rate;
        }

        log.warn("Could not find exchange rate for {} to {}. Using 1.0", from, to);
        return BigDecimal.ONE;
    }

    private BigDecimal fetchRate(String from, String to) {
        String symbol = from + to + "=X";
        String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d", symbol);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(responseEntity.getBody());
            JsonNode result = root.path("chart").path("result").get(0);
            
            if (result != null && result.has("meta")) {
                double price = result.path("meta").path("regularMarketPrice").asDouble();
                return BigDecimal.valueOf(price);
            }
        } catch (Exception e) {
            log.error("Error fetching exchange rate for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null || fromCurrency == null || toCurrency == null || fromCurrency.equals(toCurrency)) {
            return amount;
        }
        BigDecimal rate = getExchangeRate(fromCurrency, toCurrency);
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
