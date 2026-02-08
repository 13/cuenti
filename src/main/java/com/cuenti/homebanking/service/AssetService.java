package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Asset;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.repository.AssetRepository;
import com.cuenti.homebanking.repository.TransactionRepository;
import com.cuenti.homebanking.repository.ScheduledTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    @Transactional
    public void updatePrice(Asset asset) {
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
            log.warn("Rate limited (429) by Yahoo Finance for symbol: {}. Skipping for now.", asset.getSymbol());
        } catch (Exception e) {
            log.error("Error fetching price for asset: " + asset.getSymbol(), e);
        }
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
