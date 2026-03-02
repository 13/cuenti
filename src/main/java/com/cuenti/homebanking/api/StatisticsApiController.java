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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
            @RequestParam(required = false) String end,
            @RequestParam(required = false) Long accountId) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();
        User user = userService.findByUsername(username);

        List<Transaction> transactions = transactionService.getTransactionsByUser(user);

        LocalDateTime startDate = parseStartDate(start, LocalDateTime.now().minusMonths(12));
        LocalDateTime endDate = parseEndDate(end, LocalDateTime.now());

        List<Transaction> filtered = transactions.stream()
                .filter(t -> !t.getTransactionDate().isBefore(startDate) && !t.getTransactionDate().isAfter(endDate))
                .collect(Collectors.toList());

        // Filter by specific account if requested
        if (accountId != null) {
            filtered = filtered.stream()
                    .filter(t -> {
                        if (t.getFromAccount() != null && t.getFromAccount().getId().equals(accountId)) return true;
                        if (t.getToAccount() != null && t.getToAccount().getId().equals(accountId)) return true;
                        return false;
                    })
                    .collect(Collectors.toList());
        }

        // Filter out excluded accounts (only when not filtering by specific account)
        List<Account> accounts = accountService.getAccountsByUser(user);
        if (accountId == null) {
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
        }

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

    /**
     * Parses a date string accepting both "yyyy-MM-dd" (LocalDate) and ISO LocalDateTime formats.
     * For a start date, time defaults to 00:00:00.
     */
    private LocalDateTime parseStartDate(String value, LocalDateTime fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException e2) {
                return fallback;
            }
        }
    }

    /**
     * Parses a date string accepting both "yyyy-MM-dd" (LocalDate) and ISO LocalDateTime formats.
     * For an end date, time defaults to 23:59:59.999999999.
     */
    private LocalDateTime parseEndDate(String value, LocalDateTime fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atTime(LocalTime.MAX);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException e2) {
                return fallback;
            }
        }
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
