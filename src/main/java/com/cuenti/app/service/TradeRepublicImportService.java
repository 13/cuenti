package com.cuenti.app.service;

import com.cuenti.app.model.*;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.repository.PayeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    /** Trade Republic books interest, saveback and tax rows itself; they carry no counterparty. */
    private static final String TRADE_REPUBLIC_PAYEE = "Trade Republic";

    /**
     * The export's {@code date} column is Trade Republic's local (German) date, and
     * {@code datetime} is UTC. Converting in this zone keeps both consistent no matter
     * which zone the server runs in.
     */
    private static final ZoneId TRADE_REPUBLIC_ZONE = ZoneId.of("Europe/Berlin");

    private static final Set<String> BANK_BOOKED_TYPES = Set.of(
            "INTEREST_PAYMENT", "BENEFITS_SAVEBACK", "TAX_OPTIMIZATION");

    /** Outcome of one import: transactions written, rows skipped as duplicates/empty, rows that failed. */
    public record ImportResult(int imported, int skipped, int failed) {
    }

    /**
     * Imports a Trade Republic CSV. Deliberately not one database transaction: every
     * row is saved on its own, so a single bad row is counted as failed instead of
     * rolling back (or silently truncating) the whole import.
     */
    public ImportResult importCsv(InputStream inputStream, Account cashAccount, Account assetAccount, User user) throws Exception {
        if (cashAccount != null && assetAccount != null && Objects.equals(cashAccount.getId(), assetAccount.getId())) {
            throw new IllegalArgumentException("Cash and asset account must differ");
        }
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || header.trim().isEmpty()) {
                return new ImportResult(0, 0, 0);
            }

            boolean isTransactionExport = header.contains("\"datetime\"") || header.contains("datetime");
            Map<String, Integer> headerMap = isTransactionExport ? buildHeaderMap(parseCsvLine(header)) : Collections.emptyMap();
            List<Asset> assets = new ArrayList<>(assetService.getAllAssets());

            String line;
            int rowNumber = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("PANORAMICA") || line.startsWith("al ") || line.startsWith("CONTI FIDUCIARI") || line.startsWith("FONDI DEL MERCATO") || line.startsWith("NOTE SULL'ESTRATTO")) {
                    continue;
                }

                try {
                    int saved;
                    if (isTransactionExport) {
                        List<String> columns = parseCsvLine(line);
                        saved = parseAndProcessTransactionExportRow(columns, headerMap, cashAccount, assetAccount, user, assets, rowNumber++);
                    } else {
                        String[] columns = line.split(";");
                        if (columns.length < 5) {
                            continue;
                        }
                        saved = parseAndProcessLegacyRow(columns, cashAccount, assetAccount, user, assets, rowNumber++);
                    }
                    if (saved > 0) {
                        imported += saved;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Error importing Trade Republic row: " + line, e);
                }
            }
        }
        return new ImportResult(imported, skipped, failed);
    }

    private int parseAndProcessLegacyRow(String[] columns, Account cashAccount, Account assetAccount, User user,
                                         List<Asset> assets, int rowNumber) {
        String dateStr = columns[0].trim();
        String typeStr = columns[1].trim();
        String originalDescription = columns[2].trim();
        String cleanedPayeeName = cleanPayeeName(originalDescription);
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
            return 0;
        }

        boolean isAssetTrade = typeStr.equalsIgnoreCase("Commercio") ||
                                originalDescription.startsWith("Buy trade") ||
                                originalDescription.startsWith("Savings plan execution");

        // Deduplication: Skip if transaction already exists to prevent double balance impact
        Account contextAccount = isAssetTrade ? assetAccount : cashAccount;
        if (existsOnDate(contextAccount, date, amount)) {
            log.debug("Skipping duplicate transaction: {} on {} for €{}", originalDescription, date, amount);
            return 0;
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
            processAssetInfo(transaction, originalDescription, assets);

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

        ensurePayeeExists(user, transaction.getPayee());
        transactionService.saveTransaction(transaction);
        return 1;
    }

    /** @return number of transactions written for this row (0 when skipped) */
    private int parseAndProcessTransactionExportRow(List<String> columns,
                                                    Map<String, Integer> headerMap,
                                                    Account cashAccount,
                                                    Account assetAccount,
                                                    User user,
                                                    List<Asset> assets,
                                                    int rowNumber) {
        String type = getColumn(columns, headerMap, "type").toUpperCase(Locale.ROOT);
        String description = getColumn(columns, headerMap, "description");
        String name = getColumn(columns, headerMap, "name");
        String symbol = getColumn(columns, headerMap, "symbol");
        String shares = getColumn(columns, headerMap, "shares");
        String assetClass = getColumn(columns, headerMap, "asset_class");
        String currency = getColumn(columns, headerMap, "currency");
        String counterparty = getColumn(columns, headerMap, "counterparty_name");
        String transactionId = getColumn(columns, headerMap, "transaction_id");

        BigDecimal amount = parsePlainAmount(getColumn(columns, headerMap, "amount"));
        BigDecimal charges = parsePlainAmount(getColumn(columns, headerMap, "fee"))
                .add(parsePlainAmount(getColumn(columns, headerMap, "tax")));

        LocalDateTime transactionDateTime = parseTransactionDateTime(
                getColumn(columns, headerMap, "datetime"),
                getColumn(columns, headerMap, "date")
        );
        Transaction.PaymentMethod paymentMethod = mapPaymentMethod(type);

        boolean isAssetTrade = "BUY".equals(type) || "SELL".equals(type)
                || description.startsWith("Buy trade")
                || description.startsWith("Savings plan execution");

        if (isAssetTrade) {
            // Fees and taxes are part of what the trade costs (or yields): book the net amount.
            BigDecimal netAmount = amount.add(charges);
            if (netAmount.signum() == 0 || isDuplicate(transactionId, assetAccount, transactionDateTime, netAmount.abs(), user)) {
                return 0;
            }
            Transaction transaction = newTransaction(transactionId, description, paymentMethod, transactionDateTime, rowNumber);
            transaction.setType(Transaction.TransactionType.TRANSFER);
            transaction.setAmount(netAmount.abs());
            boolean isSale = netAmount.signum() > 0;
            transaction.setFromAccount(isSale ? assetAccount : cashAccount);
            transaction.setToAccount(isSale ? cashAccount : assetAccount);
            processAssetInfo(transaction, description, symbol, shares, name, assetClass, currency, assets);
            transaction.setPayee(resolvePayee(type, counterparty, name, description, transaction));
            ensurePayeeExists(user, transaction.getPayee());
            transactionService.saveTransaction(transaction);
            return 1;
        }

        // Cash rows: the gross amount and its fees/taxes are separate bookings, so e.g.
        // interest shows the full income and the withheld tax as its own expense.
        int saved = 0;
        if (amount.signum() != 0 && !isDuplicate(transactionId, cashAccount, transactionDateTime, amount.abs(), user)) {
            Transaction transaction = newTransaction(transactionId, description, paymentMethod, transactionDateTime, rowNumber);
            bookOnCash(transaction, amount, cashAccount);
            transaction.setPayee(resolvePayee(type, counterparty, name, description, transaction));
            ensurePayeeExists(user, transaction.getPayee());
            transactionService.saveTransaction(transaction);
            saved++;
        }
        String chargesId = transactionId.isEmpty() ? "" : transactionId + "-tax";
        if (charges.signum() != 0 && !isDuplicate(chargesId, cashAccount, transactionDateTime, charges.abs(), user)) {
            Transaction transaction = newTransaction(chargesId, description, paymentMethod, transactionDateTime, rowNumber);
            bookOnCash(transaction, charges, cashAccount);
            transaction.setPayee(TRADE_REPUBLIC_PAYEE);
            ensurePayeeExists(user, transaction.getPayee());
            transactionService.saveTransaction(transaction);
            saved++;
        }
        return saved;
    }

    private Transaction newTransaction(String number, String description, Transaction.PaymentMethod paymentMethod,
                                       LocalDateTime date, int rowNumber) {
        Transaction transaction = new Transaction();
        transaction.setMemo(description);
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setSortOrder(rowNumber);
        transaction.setPaymentMethod(paymentMethod);
        transaction.setTransactionDate(date);
        if (!number.isEmpty()) {
            transaction.setNumber(number);
        }
        return transaction;
    }

    /** Positive amounts are income into the cash account, negative ones expenses from it. */
    private void bookOnCash(Transaction transaction, BigDecimal signedAmount, Account cashAccount) {
        boolean isIncome = signedAmount.signum() > 0;
        transaction.setType(isIncome ? Transaction.TransactionType.INCOME : Transaction.TransactionType.EXPENSE);
        transaction.setAmount(signedAmount.abs());
        transaction.setFromAccount(isIncome ? null : cashAccount);
        transaction.setToAccount(isIncome ? cashAccount : null);
    }

    /** Prefers the transaction id when the export has one, else falls back to account + date + amount. */
    private boolean isDuplicate(String transactionId, Account account, LocalDateTime date, BigDecimal absoluteAmount, User user) {
        boolean duplicate = transactionId.isEmpty()
                ? existsOnDate(account, date.toLocalDate(), absoluteAmount)
                : transactionRepository.existsByNumberForUser(transactionId, user);
        if (duplicate) {
            log.debug("Skipping duplicate Trade Republic transaction {} on {} for {}", transactionId, date, absoluteAmount);
        }
        return duplicate;
    }

    private boolean existsOnDate(Account account, LocalDate date, BigDecimal absoluteAmount) {
        return transactionRepository.findByAccount(account).stream()
                .anyMatch(t -> t.getTransactionDate().toLocalDate().equals(date)
                        && t.getAmount().compareTo(absoluteAmount) == 0);
    }

    /** Makes imported payees show up in payee management (and its autocomplete). */
    private void ensurePayeeExists(User user, String name) {
        if (user == null || name == null || name.isBlank()) return;
        if (!payeeRepository.existsByUserAndNameIgnoreCase(user, name)) {
            payeeRepository.save(Payee.builder().user(user).name(name).build());
        }
    }

    private Optional<Asset> findAsset(List<Asset> assets, String symbol) {
        return assets.stream()
                .filter(a -> a.getSymbol().equalsIgnoreCase(symbol)
                        || (symbol.equals("IE00BK5BQT80") && a.getSymbol().equals("VWCE.DE")))
                .findFirst();
    }

    private void processAssetInfo(Transaction t, String description, List<Asset> assets) {
        Pattern isinPattern = Pattern.compile("([A-Z]{2}[A-Z0-9]{9}[0-9])");
        Pattern qtyPattern = Pattern.compile("quantity: ([0-9.]+)");

        Matcher isinMatcher = isinPattern.matcher(description);
        Matcher qtyMatcher = qtyPattern.matcher(description);

        if (t.getAsset() == null && isinMatcher.find()) {
            findAsset(assets, isinMatcher.group(1)).ifPresent(t::setAsset);
        }

        if (t.getUnits() == null && qtyMatcher.find()) {
            try {
                t.setUnits(new BigDecimal(qtyMatcher.group(1)));
            } catch (Exception e) {
                log.warn("Could not parse quantity from: " + description);
            }
        }
    }

    private void processAssetInfo(Transaction t, String description, String symbol, String shares, String name,
                                  String assetClass, String currency, List<Asset> assets) {
        String trimmedSymbol = symbol == null ? "" : symbol.trim();
        if (!trimmedSymbol.isEmpty()) {
            findAsset(assets, trimmedSymbol).ifPresent(t::setAsset);
        }

        String sharesValue = shares == null ? "" : shares.trim();
        if (!sharesValue.isEmpty()) {
            try {
                t.setUnits(new BigDecimal(sharesValue));
            } catch (Exception ignored) {
                // Fallback to description parsing below
            }
        }

        processAssetInfo(t, description, assets);

        if (t.getAsset() == null && !trimmedSymbol.isEmpty()) {
            t.setAsset(createAsset(trimmedSymbol, name, assetClass, currency, assets));
        }
    }

    /** Trades of a security the user does not track yet create it, so holdings are linked to an asset. */
    private Asset createAsset(String symbol, String name, String assetClass, String currency, List<Asset> assets) {
        Asset asset = new Asset();
        asset.setSymbol(symbol);
        asset.setName(name == null || name.isBlank() ? symbol : name.trim());
        asset.setType(switch (assetClass.toUpperCase(Locale.ROOT)) {
            case "FUND", "ETF" -> Asset.AssetType.ETF;
            case "CRYPTO" -> Asset.AssetType.CRYPTO;
            default -> Asset.AssetType.STOCK;
        });
        asset.setCurrency(currency.isBlank() ? "EUR" : currency);
        Asset saved = assetService.saveAsset(asset);
        if (saved != null) {
            assets.add(saved);
        }
        return saved;
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
        LocalDateTime dateTime = null;
        if (dateTimeStr != null && !dateTimeStr.isBlank()) {
            try {
                dateTime = OffsetDateTime.parse(dateTimeStr.trim()).atZoneSameInstant(TRADE_REPUBLIC_ZONE).toLocalDateTime();
            } catch (Exception ignored) {
                // Fallback to date-only parsing
            }
        }

        if (dateStr != null && !dateStr.isBlank()) {
            try {
                // The date column is authoritative for the booking day.
                LocalDate date = LocalDate.parse(dateStr.trim());
                return date.atTime(dateTime != null ? dateTime.toLocalTime() : LocalTime.MIDNIGHT);
            } catch (Exception ignored) {
                // Fallback below
            }
        }

        return dateTime != null ? dateTime : LocalDateTime.now();
    }

    private Transaction.PaymentMethod mapPaymentMethod(String tradeRepublicType) {
        if (tradeRepublicType == null) {
            return Transaction.PaymentMethod.NONE;
        }

        return switch (tradeRepublicType.trim().toUpperCase(Locale.ROOT)) {
            case "CARD_TRANSACTION", "CARD_TRANSACTION_INTERNATIONAL" -> Transaction.PaymentMethod.CARD_TRANSACTION;
            case "TRANSFER_INSTANT_INBOUND", "TRANSFER_INSTANT_OUTBOUND",
                 "TRANSFER_INBOUND", "TRANSFER_OUTBOUND" -> Transaction.PaymentMethod.TRANSFER;
            case "BUY", "SELL" -> Transaction.PaymentMethod.TRADE;
            case "INTEREST_PAYMENT" -> Transaction.PaymentMethod.INTEREST;
            case "BENEFITS_SAVEBACK" -> Transaction.PaymentMethod.REWARD;
            case "TAX_OPTIMIZATION" -> Transaction.PaymentMethod.FI_FEE;
            default -> Transaction.PaymentMethod.fromLabel(tradeRepublicType);
        };
    }

    private String resolvePayee(String type, String counterparty, String name, String description, Transaction transaction) {
        if (BANK_BOOKED_TYPES.contains(type)) {
            return TRADE_REPUBLIC_PAYEE;
        }
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
        // The export glues a literal "null" to some descriptions ("SHOP NAMEnull").
        return value.trim().replaceAll("null$", "").trim();
    }
}
