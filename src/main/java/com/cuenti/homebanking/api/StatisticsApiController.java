package com.cuenti.homebanking.api;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.model.Transaction;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.service.*;
import lombok.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsApiController {

    private final TransactionService transactionService;
    private final ExchangeRateService exchangeRateService;
    private final UserService userService;
    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<StatisticsResponse> getStatistics(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        List<Transaction> transactions = transactionService.getTransactionsByUser(user);

        LocalDateTime startDate = start != null ? LocalDateTime.parse(start) : LocalDateTime.now().minusMonths(12);
        LocalDateTime endDate = end != null ? LocalDateTime.parse(end) : LocalDateTime.now();

        List<Transaction> filtered = transactions.stream()
                .filter(t -> !t.getTransactionDate().isBefore(startDate) && !t.getTransactionDate().isAfter(endDate))
                .collect(Collectors.toList());

        // Filter out excluded accounts
        List<Account> accounts = accountService.getAccountsByUser(user);
        Set<Long> excludedAccountIds = accounts.stream()
                .filter(Account::isExcludeFromReports)
                .map(Account::getId)
                .collect(Collectors.toSet());

        filtered = filtered.stream()
                .filter(t -> {
                    if (t.getFromAccount() != null && excludedAccountIds.contains(t.getFromAccount().getId())) return false;
                    if (t.getToAccount() != null && excludedAccountIds.contains(t.getToAccount().getId())) return false;
                    return true;
                })
                .collect(Collectors.toList());

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        Map<String, BigDecimal> incomeByCategory = new TreeMap<>();
        Map<String, BigDecimal> expenseByCategory = new TreeMap<>();
        Map<String, BigDecimal> monthlyIncome = new TreeMap<>();
        Map<String, BigDecimal> monthlyExpense = new TreeMap<>();

        for (Transaction t : filtered) {
            String currency = getCurrency(t, user);
            BigDecimal converted = exchangeRateService.convert(t.getAmount(), currency, user.getDefaultCurrency());

            String monthKey = t.getTransactionDate().getYear() + "-" +
                    String.format("%02d", t.getTransactionDate().getMonthValue());

            String categoryName = t.getCategory() != null ? t.getCategory().getFullName() : "Uncategorized";

            if (t.getType() == Transaction.TransactionType.INCOME) {
                totalIncome = totalIncome.add(converted);
                incomeByCategory.merge(categoryName, converted, BigDecimal::add);
                monthlyIncome.merge(monthKey, converted, BigDecimal::add);
            } else if (t.getType() == Transaction.TransactionType.EXPENSE) {
                totalExpense = totalExpense.add(converted);
                expenseByCategory.merge(categoryName, converted, BigDecimal::add);
                monthlyExpense.merge(monthKey, converted, BigDecimal::add);
            }
        }

        return ResponseEntity.ok(StatisticsResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(totalIncome.subtract(totalExpense))
                .currency(user.getDefaultCurrency())
                .incomeByCategory(incomeByCategory)
                .expenseByCategory(expenseByCategory)
                .monthlyIncome(monthlyIncome)
                .monthlyExpense(monthlyExpense)
                .transactionCount(filtered.size())
                .build());
    }

    private String getCurrency(Transaction t, User user) {
        if (t.getFromAccount() != null) return t.getFromAccount().getCurrency();
        if (t.getToAccount() != null) return t.getToAccount().getCurrency();
        return user.getDefaultCurrency();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatisticsResponse {
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal balance;
        private String currency;
        private Map<String, BigDecimal> incomeByCategory;
        private Map<String, BigDecimal> expenseByCategory;
        private Map<String, BigDecimal> monthlyIncome;
        private Map<String, BigDecimal> monthlyExpense;
        private int transactionCount;
    }
}
