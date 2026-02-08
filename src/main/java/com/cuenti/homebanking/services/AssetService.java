package com.cuenti.homebanking.services;

import com.cuenti.homebanking.data.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        Asset toDelete = assetRepository.findByIdAndUser(asset.getId(), currentUser)
            .orElseThrow(() -> new RuntimeException("Asset not found or access denied"));

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
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(responseEntity.getBody());
            JsonNode result = root.path("chart").path("result").get(0);

            if (result != null && result.has("meta")) {
                double price = result.path("meta").path("regularMarketPrice").asDouble();
                String currency = result.path("meta").path("currency").asText();

                asset.setCurrentPrice(BigDecimal.valueOf(price));
                if (currency != null && !currency.isEmpty()) {
                    asset.setCurrency(currency);
                }
                asset.setLastUpdate(LocalDateTime.now());
                assetRepository.save(asset);
                log.debug("Updated price for {}: {} {}", asset.getSymbol(), price, currency);
            }
        } catch (Exception e) {
            log.warn("Could not fetch price for {}: {}", asset.getSymbol(), e.getMessage());
        }
    }

    @Transactional
    public void updateAllPrices() {
        log.info("Updating all asset prices...");
        List<Asset> allAssets = assetRepository.findAll();
        for (Asset asset : allAssets) {
            try {
                updatePrice(asset);
                Thread.sleep(500); // Rate limiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Asset price update complete");
    }

    /**
     * Update prices for all assets belonging to the current user.
     */
    @Transactional
    public void updateCurrentUserAssetPrices() {
        log.info("Updating asset prices for current user...");
        List<Asset> userAssets = getAllAssets();
        for (Asset asset : userAssets) {
            try {
                updatePrice(asset);
                Thread.sleep(500); // Rate limiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("User asset price update complete");
    }
}
