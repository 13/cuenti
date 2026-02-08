package com.cuenti.homebanking.services;

import com.cuenti.homebanking.data.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for importing Trade Republic CSV statements.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeRepublicImportService {

    private final TransactionService transactionService;
    private final AssetService assetService;
    private final CategoryService categoryService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Transactional
    public void importCsv(InputStream inputStream, Account cashAccount, Account assetAccount) throws Exception {
        log.info("Starting Trade Republic CSV import");

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        int lineNumber = 0;
        int imported = 0;
        List<String> errors = new ArrayList<>();

        // Skip header line
        String header = reader.readLine();
        lineNumber++;

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            try {
                if (line.trim().isEmpty()) continue;

                String[] parts = parseCsvLine(line);
                if (parts.length < 5) {
                    errors.add("Line " + lineNumber + ": Not enough columns");
                    continue;
                }

                // Trade Republic CSV format: Date, Type, Value, Currency, Description, etc.
                String dateStr = parts[0].trim();
                String type = parts[1].trim();
                String valueStr = parts[2].trim().replace(",", ".");
                String currency = parts[3].trim();
                String description = parts.length > 4 ? parts[4].trim() : "";

                LocalDateTime date;
                try {
                    date = LocalDateTime.parse(dateStr, DATE_FORMATTER);
                } catch (Exception e) {
                    // Try alternative format
                    try {
                        date = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
                    } catch (Exception e2) {
                        date = LocalDateTime.now();
                    }
                }

                BigDecimal value = new BigDecimal(valueStr);

                Transaction.TransactionType transactionType;
                Account fromAccount = null;
                Account toAccount = null;

                // Determine transaction type based on Trade Republic type field
                if (type.toLowerCase().contains("einzahlung") ||
                    type.toLowerCase().contains("deposit") ||
                    type.toLowerCase().contains("gutschrift")) {
                    transactionType = Transaction.TransactionType.INCOME;
                    toAccount = cashAccount;
                } else if (type.toLowerCase().contains("auszahlung") ||
                           type.toLowerCase().contains("withdrawal") ||
                           type.toLowerCase().contains("gebühr") ||
                           type.toLowerCase().contains("fee")) {
                    transactionType = Transaction.TransactionType.EXPENSE;
                    fromAccount = cashAccount;
                } else if (type.toLowerCase().contains("kauf") ||
                           type.toLowerCase().contains("buy") ||
                           type.toLowerCase().contains("order")) {
                    // Asset purchase - expense from cash
                    transactionType = Transaction.TransactionType.EXPENSE;
                    fromAccount = cashAccount;
                } else if (type.toLowerCase().contains("verkauf") ||
                           type.toLowerCase().contains("sell")) {
                    // Asset sale - income to cash
                    transactionType = Transaction.TransactionType.INCOME;
                    toAccount = cashAccount;
                } else if (type.toLowerCase().contains("dividende") ||
                           type.toLowerCase().contains("dividend")) {
                    transactionType = Transaction.TransactionType.INCOME;
                    toAccount = cashAccount;
                } else {
                    // Default: if positive it's income, if negative it's expense
                    if (value.compareTo(BigDecimal.ZERO) >= 0) {
                        transactionType = Transaction.TransactionType.INCOME;
                        toAccount = cashAccount;
                    } else {
                        transactionType = Transaction.TransactionType.EXPENSE;
                        fromAccount = cashAccount;
                        value = value.abs();
                    }
                }

                Transaction t = Transaction.builder()
                        .type(transactionType)
                        .amount(value.abs())
                        .transactionDate(date)
                        .fromAccount(fromAccount)
                        .toAccount(toAccount)
                        .payee("Trade Republic")
                        .memo(type + ": " + description)
                        .build();

                transactionService.saveTransaction(t);
                imported++;

            } catch (Exception e) {
                errors.add("Line " + lineNumber + ": " + e.getMessage());
                log.warn("Error importing line {}: {}", lineNumber, e.getMessage());
            }
        }

        reader.close();

        if (!errors.isEmpty()) {
            log.warn("Trade Republic import completed with {} errors", errors.size());
        }

        log.info("Trade Republic import complete: {} transactions imported", imported);
    }

    /**
     * Parse a CSV line handling quoted fields.
     */
    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());

        return result.toArray(new String[0]);
    }
}
