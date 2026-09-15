package com.cuenti.app.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ExchangeRateService {

    /** How long a fetched rate is reused before asking the provider again. */
    static final Duration RATE_TTL = Duration.ofHours(6);
    /**
     * How long a failed lookup is remembered. Without this, every conversion
     * (e.g. one per grid row) retries the HTTP call while offline.
     */
    static final Duration FAILURE_TTL = Duration.ofMinutes(10);

    private record CachedRate(BigDecimal rate, Instant expiresAt) {}

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CachedRate> rateCache = new ConcurrentHashMap<>();

    public ExchangeRateService() {
        this(createRestTemplate());
    }

    /** Package-visible for tests. */
    ExchangeRateService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    public BigDecimal getExchangeRate(String from, String to) {
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }

        String pair = from + to;
        Instant now = Instant.now();
        CachedRate cached = rateCache.get(pair);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.rate();
        }

        BigDecimal rate = fetchRate(from, to);
        if (rate == null) {
            // Try inverse if direct failed
            BigDecimal inverseRate = fetchRate(to, from);
            if (inverseRate != null && inverseRate.compareTo(BigDecimal.ZERO) != 0) {
                rate = BigDecimal.ONE.divide(inverseRate, 10, RoundingMode.HALF_UP);
            }
        }
        if (rate != null) {
            rateCache.put(pair, new CachedRate(rate, now.plus(RATE_TTL)));
            return rate;
        }

        // Keep serving the last known rate rather than falling back to 1.0
        BigDecimal fallback = cached != null ? cached.rate() : BigDecimal.ONE;
        log.warn("Could not find exchange rate for {} to {}. Using {}", from, to, fallback);
        rateCache.put(pair, new CachedRate(fallback, now.plus(FAILURE_TTL)));
        return fallback;
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
