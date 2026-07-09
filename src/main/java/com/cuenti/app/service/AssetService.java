package com.cuenti.app.service;

import com.cuenti.app.model.Asset;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.AssetRepository;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.repository.ScheduledTransactionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetService {

    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final ScheduledTransactionRepository scheduledTransactionRepository;
    private final UserService userService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Asset> getAllAssets() {
        String username = SecurityUtil.getAuthenticatedUsername()
            .orElseThrow(() -> new RuntimeException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        return assetRepository.findByUser(currentUser);
    }

    public List<Asset> searchAssets(String searchTerm) {
        String username = SecurityUtil.getAuthenticatedUsername()
            .orElseThrow(() -> new RuntimeException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        if (searchTerm == null || searchTerm.isEmpty()) {
            return assetRepository.findByUser(currentUser);
        }
        return assetRepository.findByUserAndSymbolContainingIgnoreCaseOrUserAndNameContainingIgnoreCase(
            currentUser, searchTerm, currentUser, searchTerm);
    }

    @Transactional
    public Asset saveAsset(Asset asset) {
        String username = SecurityUtil.getAuthenticatedUsername()
            .orElseThrow(() -> new RuntimeException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        asset.setUser(currentUser);
        Asset saved = assetRepository.save(asset);
        updatePrice(saved);
        return saved;
    }

    @Transactional
    public void deleteAsset(Asset asset) {
        String username = SecurityUtil.getAuthenticatedUsername()
            .orElseThrow(() -> new RuntimeException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        // Verify ownership
        Asset toDelete = assetRepository.findByIdAndUser(asset.getId(), currentUser)
            .orElseThrow(() -> new RuntimeException("Asset not found or access denied"));

        // Check if the asset is being used in any transactions
        long transactionCount = transactionRepository.countByAsset(toDelete);
        long scheduledTransactionCount = scheduledTransactionRepository.countByAsset(toDelete);

        if (transactionCount > 0 || scheduledTransactionCount > 0) {
            throw new IllegalStateException(
                "Cannot delete asset '" + toDelete.getName() +
                "' because it is referenced by " + transactionCount +
                " transaction(s) and " + scheduledTransactionCount +
                " scheduled transaction(s). Please remove those references first.");
        }

        assetRepository.delete(toDelete);
    }

    private HttpHeaders browserHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        return headers;
    }

    private void applyPriceResponse(Asset asset, String response) {
        JsonNode root = objectMapper.readTree(response);
        JsonNode resultArr = root.path("chart").path("result");
        if (resultArr.isArray() && !resultArr.isEmpty()) {
            JsonNode meta = resultArr.get(0).path("meta");
            if (!meta.isMissingNode()) {
                asset.setCurrentPrice(BigDecimal.valueOf(meta.path("regularMarketPrice").asDouble()));
                asset.setCurrency(meta.path("currency").asText());
                asset.setLastUpdate(LocalDateTime.now());
                assetRepository.save(asset);
                log.info("Updated price for {} after retry", asset.getSymbol());
            }
        }
    }

    /** Prices younger than this are not refetched. */
    private static final java.time.Duration PRICE_FRESHNESS = java.time.Duration.ofMinutes(10);

    @Transactional
    public void updatePrice(Asset asset) {
        if (asset.getLastUpdate() != null && asset.getCurrentPrice() != null
                && asset.getLastUpdate().isAfter(LocalDateTime.now().minus(PRICE_FRESHNESS))) {
            log.debug("Skipping fresh price for {}", asset.getSymbol());
            return;
        }
        String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d", asset.getSymbol());
        
        try {
            // Use a real browser User-Agent to avoid 429/403 errors
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String response = responseEntity.getBody();

            JsonNode root = objectMapper.readTree(response);
            JsonNode resultArr = root.path("chart").path("result");
            if (resultArr.isArray() && !resultArr.isEmpty()) {
                JsonNode result = resultArr.get(0);
                if (result.has("meta")) {
                    JsonNode meta = result.path("meta");
                    double price = meta.path("regularMarketPrice").asDouble();
                    String currency = meta.path("currency").asText();
                    
                    asset.setCurrentPrice(BigDecimal.valueOf(price));
                    asset.setCurrency(currency);
                    asset.setLastUpdate(LocalDateTime.now());
                    assetRepository.save(asset);
                    log.info("Updated price for {}: {} {}", asset.getSymbol(), price, currency);
                }
            }
        } catch (HttpClientErrorException.TooManyRequests e) {
            // back off with jitter and retry once; give up until the next cycle otherwise
            long delayMs = 3000 + java.util.concurrent.ThreadLocalRandom.current().nextLong(3000);
            log.warn("Rate limited (429) for {}. Backing off {} ms.", asset.getSymbol(), delayMs);
            try {
                Thread.sleep(delayMs);
                ResponseEntity<String> retry = restTemplate.exchange(url, HttpMethod.GET,
                        new HttpEntity<>(browserHeaders()), String.class);
                applyPriceResponse(asset, retry.getBody());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception retryEx) {
                log.warn("Retry after 429 failed for {}: {}", asset.getSymbol(), retryEx.getMessage());
            }
        } catch (Exception e) {
            log.error("Error fetching price for asset: " + asset.getSymbol(), e);
        }
    }

    /** Per-user throttle so navigating between views doesn't refetch prices on every page load. */
    private static final Duration PRICE_UPDATE_THROTTLE = Duration.ofMinutes(15);
    private final Map<Long, Instant> lastUserPriceUpdate = new ConcurrentHashMap<>();

    /**
     * Async, throttled variant used by the UI on navigation: runs on Spring's
     * task executor (no ad-hoc threads) and skips the update when prices were
     * already refreshed for this user within the throttle window. The hourly
     * scheduled job keeps prices fresh in between.
     */
    @Async("priceExecutor")
    @Transactional
    public void updateUserAssetPricesThrottled(User user) {
        if (!markPriceUpdateDue(user.getId(), Instant.now())) {
            log.debug("Skipping asset price update for user {} (throttled)", user.getUsername());
            return;
        }
        updateUserAssetPrices(user);
    }

    /**
     * Returns true and records the run when a price update is due for the
     * user, false while still inside the throttle window.
     */
    boolean markPriceUpdateDue(Long userId, Instant now) {
        Instant last = lastUserPriceUpdate.get(userId);
        if (last != null && now.isBefore(last.plus(PRICE_UPDATE_THROTTLE))) {
            return false;
        }
        lastUserPriceUpdate.put(userId, now);
        return true;
    }

    /**
     * Update all assets for a specific user.
     * This is called when a user logs in.
     */
    @Transactional
    public void updateUserAssetPrices(User user) {
        log.info("Updating asset prices for user: {}", user.getUsername());
        List<Asset> assets = assetRepository.findByUser(user);
        for (Asset asset : assets) {
            updatePrice(asset);
            try {
                // Add a small delay between requests to be nice to the API
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Asset price update interrupted for user: {}", user.getUsername());
                break;
            }
        }
        log.info("Completed asset price update for user: {}", user.getUsername());
    }

    /**
     * Update asset prices for the currently authenticated user.
     */
    @Transactional
    public void updateCurrentUserAssetPrices() {
        String username = SecurityUtil.getAuthenticatedUsername()
            .orElseThrow(() -> new RuntimeException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        updateUserAssetPrices(currentUser);
    }

    /**
     * Automatically update all asset prices every hour with a small delay between requests.
     * Updates assets for all users.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void updateAllPrices() {
        log.info("Starting scheduled asset price update...");
        // Get all assets across all users for scheduled updates
        List<Asset> assets = assetRepository.findAll();
        for (Asset asset : assets) {
            updatePrice(asset);
            try {
                // Add a small delay between requests to be nice to the API
                Thread.sleep(2000); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
