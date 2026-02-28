package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.DashboardDTO;
import com.cuenti.homebanking.api.dto.DtoMapper;
import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardApiController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final AssetService assetService;
    private final ExchangeRateService exchangeRateService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<DashboardDTO> getDashboard() {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        List<Account> allAccounts = accountService.getAccountsByUser(user);
        List<Account> accounts = allAccounts.stream()
                .filter(a -> !a.isExcludeFromSummary())
                .collect(Collectors.toList());
        List<Transaction> transactions = transactionService.getTransactionsByUser(user);

        // Calculate available cash (non-asset accounts)
        BigDecimal availableCash = accounts.stream()
                .filter(a -> a.getAccountType() != Account.AccountType.ASSET)
                .map(a -> exchangeRateService.convert(a.getBalance(), a.getCurrency(), user.getDefaultCurrency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate asset performance
        List<DashboardDTO.AssetPerformanceDTO> assetPerformance = calculateAssetPerformance(transactions, user);

        // Portfolio value from asset performance
        BigDecimal portfolioValue = assetPerformance.stream()
                .map(DashboardDTO.AssetPerformanceDTO::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fallback to asset account balances
        if (portfolioValue.compareTo(BigDecimal.ZERO) == 0) {
            portfolioValue = accounts.stream()
                    .filter(a -> a.getAccountType() == Account.AccountType.ASSET)
                    .map(a -> exchangeRateService.convert(a.getBalance(), a.getCurrency(), user.getDefaultCurrency()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal netWorth = availableCash.add(portfolioValue);

        return ResponseEntity.ok(DashboardDTO.builder()
                .availableCash(availableCash)
                .portfolioValue(portfolioValue)
                .netWorth(netWorth)
                .defaultCurrency(user.getDefaultCurrency())
                .accounts(allAccounts.stream().map(DtoMapper::toAccountDTO).collect(Collectors.toList()))
                .assetPerformance(assetPerformance)
                .build());
    }

    private List<DashboardDTO.AssetPerformanceDTO> calculateAssetPerformance(List<Transaction> transactions, User user) {
        Map<Asset, List<Transaction>> assetTransactions = transactions.stream()
                .filter(t -> t.getAsset() != null && t.getUnits() != null)
                .filter(t -> t.getToAccount() != null && t.getToAccount().getAccountType() == Account.AccountType.ASSET)
                .collect(Collectors.groupingBy(Transaction::getAsset));

        List<DashboardDTO.AssetPerformanceDTO> result = new ArrayList<>();

        for (Map.Entry<Asset, List<Transaction>> entry : assetTransactions.entrySet()) {
            Asset asset = entry.getKey();
            List<Transaction> assetTxs = entry.getValue();

            BigDecimal totalUnits = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;

            for (Transaction t : assetTxs) {
                totalUnits = totalUnits.add(t.getUnits());
                totalCost = totalCost.add(t.getAmount());
            }

            BigDecimal currentPrice = asset.getCurrentPrice() != null ? asset.getCurrentPrice() : BigDecimal.ZERO;
            BigDecimal currentValue = totalUnits.multiply(currentPrice);

            BigDecimal currentValueConverted = exchangeRateService.convert(currentValue, asset.getCurrency(), user.getDefaultCurrency());
            BigDecimal totalCostConverted = totalCost;

            BigDecimal gainLoss = currentValueConverted.subtract(totalCostConverted);
            BigDecimal gainLossPercent = totalCostConverted.compareTo(BigDecimal.ZERO) > 0
                    ? gainLoss.divide(totalCostConverted, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            result.add(DashboardDTO.AssetPerformanceDTO.builder()
                    .assetName(asset.getName())
                    .assetSymbol(asset.getSymbol())
                    .totalUnits(totalUnits)
                    .totalCost(totalCostConverted)
                    .currentValue(currentValueConverted)
                    .currentPrice(currentPrice)
                    .gainLoss(gainLoss)
                    .gainLossPercent(gainLossPercent)
                    .build());
        }

        return result;
    }
}
