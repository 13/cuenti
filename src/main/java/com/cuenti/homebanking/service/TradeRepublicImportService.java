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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
            if (header == null) return;

            String line;
            int rowNumber = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("PANORAMICA") || line.startsWith("al ") || line.startsWith("CONTI FIDUCIARI") || line.startsWith("FONDI DEL MERCATO") || line.startsWith("NOTE SULL'ESTRATTO")) {
                    continue;
                }

                String[] columns = line.split(";");
                if (columns.length < 5) continue;

                try {
                    parseAndProcessRow(columns, cashAccount, assetAccount, rowNumber++);
                } catch (Exception e) {
                    log.error("Error parsing row: " + line, e);
                }
            }
        }
    }

    private void parseAndProcessRow(String[] columns, Account cashAccount, Account assetAccount, int rowNumber) {
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

    private BigDecimal parseAmount(String amountStr) {
        String clean = amountStr.replace("€", "").replace(".", "").replace(",", ".").trim();
        return new BigDecimal(clean);
    }
}
