package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.*;
import com.cuenti.homebanking.repository.TransactionRepository;
import com.cuenti.homebanking.repository.PayeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeRepublicImportService {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final PayeeRepository payeeRepository;
    private final AssetService assetService;

    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd MMM yyyy")
            .toFormatter(Locale.ITALIAN);

    @Transactional
    public void importCsv(InputStream inputStream, Account cashAccount, Account assetAccount) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || header.trim().isEmpty()) {
                return;
            }

            boolean isTransactionExport = header.contains("\"datetime\"") || header.contains("datetime");
            Map<String, Integer> headerMap = isTransactionExport ? buildHeaderMap(parseCsvLine(header)) : Collections.emptyMap();

            String line;
            int rowNumber = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("PANORAMICA") || line.startsWith("al ") || line.startsWith("CONTI FIDUCIARI") || line.startsWith("FONDI DEL MERCATO") || line.startsWith("NOTE SULL'ESTRATTO")) {
                    continue;
                }

                try {
                    if (isTransactionExport) {
                        List<String> columns = parseCsvLine(line);
                        parseAndProcessTransactionExportRow(columns, headerMap, cashAccount, assetAccount, rowNumber++);
                    } else {
                        String[] columns = line.split(";");
                        if (columns.length < 5) {
                            continue;
                        }
                        parseAndProcessLegacyRow(columns, cashAccount, assetAccount, rowNumber++);
                    }
                } catch (Exception e) {
                    log.error("Error parsing row: " + line, e);
                }
            }
        }
    }

    private void parseAndProcessLegacyRow(String[] columns, Account cashAccount, Account assetAccount, int rowNumber) {
        String dateStr = columns[0].trim();
        String typeStr = columns[1].trim();
        String originalDescription = columns[2].trim();
        String cleanedPayeeName = originalDescription.replace("null", "").trim();
        String incomingStr = columns[3].trim();
        String outgoingStr = columns[4].trim();

        LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
        LocalDateTime transactionDateTime = date.atStartOfDay();

        BigDecimal amount;
        Transaction.TransactionType csvType;

        if (!incomingStr.isEmpty()) {
            amount = parseAmount(incomingStr);
            csvType = Transaction.TransactionType.INCOME;
        } else if (!outgoingStr.isEmpty()) {
            amount = parseAmount(outgoingStr);
            csvType = Transaction.TransactionType.EXPENSE;
        } else {
            return;
        }

        boolean isAssetTrade = typeStr.equalsIgnoreCase("Commercio") ||
                                originalDescription.startsWith("Buy trade") ||
                                originalDescription.startsWith("Savings plan execution");

        // Deduplication: Skip if transaction already exists to prevent double balance impact
        Account contextAccount = isAssetTrade ? assetAccount : cashAccount;
        List<Transaction> existing = transactionRepository.findByAccount(contextAccount).stream()
                .filter(t -> t.getTransactionDate().toLocalDate().equals(date))
                .filter(t -> t.getAmount().compareTo(amount) == 0)
                .toList();

        if (!existing.isEmpty()) {
            log.debug("Skipping duplicate transaction: {} on {} for €{}", originalDescription, date, amount);
            return;  // Skip duplicate - don't process again!
        }

        // Create new transaction
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(transactionDateTime);
        transaction.setAmount(amount);
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setSortOrder(rowNumber);

        // Set Memo
        transaction.setMemo(originalDescription);

        // Set Payment Method based on TR Type
        transaction.setPaymentMethod(Transaction.PaymentMethod.fromLabel(typeStr));

        // Logic: Asset trades are TRANSFERS from Cash to Asset account
        if (isAssetTrade) {
            transaction.setType(Transaction.TransactionType.TRANSFER);
            transaction.setFromAccount(cashAccount);
            transaction.setToAccount(assetAccount);
            processAssetInfo(transaction, originalDescription);
            
            // Set payee to Asset name if found
            if (transaction.getAsset() != null) {
                transaction.setPayee(transaction.getAsset().getName());
            } else {
                transaction.setPayee(cleanedPayeeName);
            }
        } else {
            transaction.setType(csvType);
            transaction.setPayee(cleanedPayeeName);
            if (csvType == Transaction.TransactionType.INCOME) {
                transaction.setToAccount(cashAccount);
                transaction.setFromAccount(null);
            } else {
                transaction.setFromAccount(cashAccount);
                transaction.setToAccount(null);
            }
        }
        
        ensurePayeeExists(transaction.getPayee());
        transactionService.saveTransaction(transaction);
    }

    private void parseAndProcessTransactionExportRow(List<String> columns,
                                                     Map<String, Integer> headerMap,
                                                     Account cashAccount,
                                                     Account assetAccount,
                                                     int rowNumber) {
        String txId = getColumn(columns, headerMap, "transaction_id");
        if (!txId.isBlank() && transactionRepository.findByNumber(txId).isPresent()) {
            log.debug("Skipping duplicate transaction by id: {}", txId);
            return;
        }

        String type = getColumn(columns, headerMap, "type");
        String description = getColumn(columns, headerMap, "description");
        String name = getColumn(columns, headerMap, "name");
        String symbol = getColumn(columns, headerMap, "symbol");
        String shares = getColumn(columns, headerMap, "shares");
        String counterparty = getColumn(columns, headerMap, "counterparty_name");

        BigDecimal amount = parsePlainAmount(getColumn(columns, headerMap, "amount"));
        BigDecimal fee = parsePlainAmount(getColumn(columns, headerMap, "fee"));
        BigDecimal tax = parsePlainAmount(getColumn(columns, headerMap, "tax"));
        BigDecimal netAmount = amount.add(fee).add(tax);
        if (netAmount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        Transaction transaction = new Transaction();
        transaction.setNumber(txId.isBlank() ? null : txId);
        transaction.setMemo(description);
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setSortOrder(rowNumber);
        transaction.setPaymentMethod(mapPaymentMethod(type));
        transaction.setTransactionDate(parseTransactionDateTime(
                getColumn(columns, headerMap, "datetime"),
                getColumn(columns, headerMap, "date")
        ));

        boolean isAssetTrade = "BUY".equalsIgnoreCase(type)
                || description.startsWith("Buy trade")
                || description.startsWith("Savings plan execution");

        if (isAssetTrade) {
            transaction.setType(Transaction.TransactionType.TRANSFER);
            transaction.setAmount(netAmount.abs());
            transaction.setFromAccount(cashAccount);
            transaction.setToAccount(assetAccount);
            processAssetInfo(transaction, description, symbol, shares, name);
            transaction.setPayee(resolvePayee(counterparty, name, description, transaction));
        } else {
            boolean isIncome = netAmount.compareTo(BigDecimal.ZERO) > 0;
            transaction.setType(isIncome ? Transaction.TransactionType.INCOME : Transaction.TransactionType.EXPENSE);
            transaction.setAmount(netAmount.abs());
            transaction.setPayee(resolvePayee(counterparty, name, description, transaction));
            if (isIncome) {
                transaction.setToAccount(cashAccount);
                transaction.setFromAccount(null);
            } else {
                transaction.setFromAccount(cashAccount);
                transaction.setToAccount(null);
            }
        }

        ensurePayeeExists(transaction.getPayee());
        transactionService.saveTransaction(transaction);
    }

    private void ensurePayeeExists(String name) {
        if (name == null || name.isEmpty()) return;
        // Note: This method should be updated to accept a User parameter
        // For now, we'll skip the duplicate check as it requires user context
        // This is a limitation of the current implementation
        // TODO: Refactor to pass User to this method
    }

    private void processAssetInfo(Transaction t, String description) {
        Pattern isinPattern = Pattern.compile("([A-Z]{2}[A-Z0-9]{9}[0-9])");
        Pattern qtyPattern = Pattern.compile("quantity: ([0-9.]+)");
        
        Matcher isinMatcher = isinPattern.matcher(description);
        Matcher qtyMatcher = qtyPattern.matcher(description);
        
        if (isinMatcher.find()) {
            String isin = isinMatcher.group(1);
            assetService.getAllAssets().stream()
                    .filter(a -> a.getSymbol().equalsIgnoreCase(isin) || 
                                 (isin.equals("IE00BK5BQT80") && a.getSymbol().equals("VWCE.DE")))
                    .findFirst()
                    .ifPresent(t::setAsset);
        }
        
        if (qtyMatcher.find()) {
            try {
                t.setUnits(new BigDecimal(qtyMatcher.group(1)));
            } catch (Exception e) {
                log.warn("Could not parse quantity from: " + description);
            }
        }
    }

    private void processAssetInfo(Transaction t, String description, String symbol, String shares, String name) {
        String trimmedSymbol = symbol == null ? "" : symbol.trim();
        if (!trimmedSymbol.isEmpty()) {
            assetService.getAllAssets().stream()
                    .filter(a -> a.getSymbol().equalsIgnoreCase(trimmedSymbol)
                            || (trimmedSymbol.equals("IE00BK5BQT80") && a.getSymbol().equals("VWCE.DE")))
                    .findFirst()
                    .ifPresent(t::setAsset);
        }

        if (t.getAsset() == null) {
            processAssetInfo(t, description);
        }

        if (t.getAsset() == null && name != null && !name.isBlank()) {
            t.setPayee(name.trim());
        }

        String sharesValue = shares == null ? "" : shares.trim();
        if (!sharesValue.isEmpty()) {
            try {
                t.setUnits(new BigDecimal(sharesValue));
                return;
            } catch (Exception ignored) {
                // Fallback to description parsing below
            }
        }

        if (t.getUnits() == null) {
            processAssetInfo(t, description);
        }
    }

    private BigDecimal parseAmount(String amountStr) {
        String clean = amountStr.replace("€", "").replace(".", "").replace(",", ".").trim();
        return new BigDecimal(clean);
    }

    private BigDecimal parsePlainAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private Map<String, Integer> buildHeaderMap(List<String> headerColumns) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerColumns.size(); i++) {
            map.put(headerColumns.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return map;
    }

    private String getColumn(List<String> columns, Map<String, Integer> headerMap, String columnName) {
        Integer index = headerMap.get(columnName.toLowerCase(Locale.ROOT));
        if (index == null || index < 0 || index >= columns.size()) {
            return "";
        }
        return columns.get(index).trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private LocalDateTime parseTransactionDateTime(String dateTimeStr, String dateStr) {
        if (dateTimeStr != null && !dateTimeStr.isBlank()) {
            try {
                return Instant.parse(dateTimeStr.trim()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception ignored) {
                try {
                    return OffsetDateTime.parse(dateTimeStr.trim()).toLocalDateTime();
                } catch (Exception ignoredAgain) {
                    // Fallback to date-only parsing
                }
            }
        }

        if (dateStr != null && !dateStr.isBlank()) {
            try {
                return LocalDate.parse(dateStr.trim()).atStartOfDay();
            } catch (Exception ignored) {
                // Fallback below
            }
        }

        return LocalDateTime.now();
    }

    private Transaction.PaymentMethod mapPaymentMethod(String tradeRepublicType) {
        if (tradeRepublicType == null) {
            return Transaction.PaymentMethod.NONE;
        }

        return switch (tradeRepublicType.trim().toUpperCase(Locale.ROOT)) {
            case "CARD_TRANSACTION" -> Transaction.PaymentMethod.CARD_TRANSACTION;
            case "TRANSFER_INSTANT_INBOUND", "TRANSFER_INSTANT_OUTBOUND" -> Transaction.PaymentMethod.TRANSFER;
            case "BUY" -> Transaction.PaymentMethod.TRADE;
            case "INTEREST_PAYMENT" -> Transaction.PaymentMethod.INTEREST;
            case "BENEFITS_SAVEBACK" -> Transaction.PaymentMethod.REWARD;
            case "TAX_OPTIMIZATION" -> Transaction.PaymentMethod.FI_FEE;
            default -> Transaction.PaymentMethod.fromLabel(tradeRepublicType);
        };
    }

    private String resolvePayee(String counterparty, String name, String description, Transaction transaction) {
        if (transaction.getAsset() != null && transaction.getAsset().getName() != null) {
            return cleanPayeeName(transaction.getAsset().getName());
        }
        if (name != null && !name.isBlank()) {
            return cleanPayeeName(name);
        }
        if (counterparty != null && !counterparty.isBlank()) {
            return cleanPayeeName(counterparty);
        }
        return cleanPayeeName(description);
    }

    private String cleanPayeeName(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("null", "").trim();
    }
}
