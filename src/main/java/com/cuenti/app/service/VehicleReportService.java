package com.cuenti.app.service;

import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fuel/vehicle cost report computed from expense transactions in a chosen
 * category. Memo syntax: "d=45210" (odometer km), "l=41.3" or "v=41.3"
 * (liters), the word "full" marks a full tank. Consumption is measured
 * full-tank to full-tank; when no entry is flagged, every fill counts.
 * Logic moved verbatim from VehiclesView so the REST API can reuse it.
 */
@Service
@RequiredArgsConstructor
public class VehicleReportService {

    private static final Pattern ODOMETER_PATTERN = Pattern.compile("d[=:]\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern LITERS_PATTERN = Pattern.compile("[vl][~=:]\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern FULL_TANK_PATTERN = Pattern.compile("\\b(full)\\b", Pattern.CASE_INSENSITIVE);

    private final TransactionService transactionService;
    private final ExchangeRateService exchangeRateService;

    @Getter
    @Setter
    public static class FuelEntry {
        private LocalDate date;
        private BigDecimal odometer;
        private BigDecimal liters;
        private BigDecimal amount;
        private String currency;
        private String station;
        private String memo;
        private boolean fullTank;
        private BigDecimal distance;
        private BigDecimal pricePerLiter;
        private BigDecimal consumption;

        public FuelEntry(LocalDate date, BigDecimal odometer, BigDecimal liters, BigDecimal amount,
                         String currency, String station, String memo) {
            this.date = date; this.odometer = odometer; this.liters = liters; this.amount = amount;
            this.currency = currency; this.station = station; this.memo = memo;
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class VehicleReport {
        private final List<FuelEntry> entries;       // date descending
        private final BigDecimal totalCost;          // in user's default currency
        private final BigDecimal totalLiters;
        private final BigDecimal totalDistance;
        private final BigDecimal avgConsumption;     // l/100km, null if unmeasurable
        private final BigDecimal avgPricePerLiter;   // null if no liters recorded
        private final String currency;
    }

    @Transactional(readOnly = true)
    public VehicleReport getReport(User user, Long categoryId, LocalDate start, LocalDate end) {
        List<Transaction> transactions = transactionService.getTransactionsByUser(user).stream()
                .filter(t -> t.getCategory() != null && t.getCategory().getId().equals(categoryId))
                .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
                .filter(t -> {
                    LocalDate txDate = t.getTransactionDate().toLocalDate();
                    return !txDate.isBefore(start) && !txDate.isAfter(end);
                })
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .collect(Collectors.toList());

        List<FuelEntry> entries = new ArrayList<>();
        for (Transaction t : transactions) {
            FuelEntry entry = parseFuelEntry(t, user.getDefaultCurrency());
            if (entry != null) entries.add(entry);
        }

        BigDecimal[] attributed = computeDerivedValues(entries);
        BigDecimal attributedLiters = attributed[0];
        BigDecimal attributedDistance = attributed[1];

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalLiters = BigDecimal.ZERO;
        for (FuelEntry e : entries) {
            totalCost = totalCost.add(exchangeRateService.convert(e.getAmount(), e.getCurrency(), user.getDefaultCurrency()));
            if (e.getLiters() != null) totalLiters = totalLiters.add(e.getLiters());
        }

        BigDecimal avgConsumption = attributedDistance.compareTo(BigDecimal.ZERO) > 0
                ? attributedLiters.divide(attributedDistance, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null;
        BigDecimal avgPricePerLiter = totalLiters.compareTo(BigDecimal.ZERO) > 0
                ? totalCost.divide(totalLiters, 3, RoundingMode.HALF_UP)
                : null;

        List<FuelEntry> descending = entries.stream()
                .sorted(Comparator.comparing(FuelEntry::getDate).reversed())
                .collect(Collectors.toList());

        return new VehicleReport(descending, totalCost, totalLiters, attributedDistance,
                avgConsumption, avgPricePerLiter, user.getDefaultCurrency());
    }

    public static FuelEntry parseFuelEntry(Transaction t, String defaultCurrency) {
        BigDecimal odometer = extractValue(t.getMemo(), ODOMETER_PATTERN, "(\\d{4,})\\s*km");
        BigDecimal liters = extractValue(t.getMemo(), LITERS_PATTERN, "(\\d+(?:[.,]\\d+)?)\\s*[Ll](?:\\s|$|\\))");
        FuelEntry entry = new FuelEntry(
                t.getTransactionDate().toLocalDate(),
                odometer,
                liters,
                t.getAmount(),
                t.getFromAccount() != null ? t.getFromAccount().getCurrency() : defaultCurrency,
                t.getPayee(),
                t.getMemo()
        );
        entry.setFullTank(extractFullTank(t.getMemo()));
        return entry;
    }

    private static BigDecimal extractValue(String memo, Pattern primary, String secondaryRegex) {
        if (memo == null || memo.isEmpty()) return null;
        Matcher m = primary.matcher(memo);
        if (m.find()) return new BigDecimal(m.group(1).replace(",", "."));
        Matcher m2 = Pattern.compile(secondaryRegex).matcher(memo);
        if (m2.find()) return new BigDecimal(m2.group(1).replace(",", "."));
        return null;
    }

    private static boolean extractFullTank(String memo) {
        if (memo == null || memo.isEmpty()) return false;
        return FULL_TANK_PATTERN.matcher(memo).find();
    }

    /**
     * Computes distance/price/consumption per entry (full-tank to full-tank;
     * fill-to-fill fallback when no entry is flagged). Returns
     * {attributedLiters, attributedDistance} for the averages.
     */
    public static BigDecimal[] computeDerivedValues(List<FuelEntry> fuelEntries) {
        BigDecimal attributedLiters = BigDecimal.ZERO;
        BigDecimal attributedDistance = BigDecimal.ZERO;

        boolean anyFullTank = fuelEntries.stream().anyMatch(FuelEntry::isFullTank);

        FuelEntry previous = null;
        FuelEntry lastFull = null;
        BigDecimal litersSinceFull = BigDecimal.ZERO;

        for (FuelEntry entry : fuelEntries) {
            if (previous != null && entry.getOdometer() != null && previous.getOdometer() != null) {
                entry.setDistance(entry.getOdometer().subtract(previous.getOdometer()));
            }
            if (entry.getLiters() != null && entry.getLiters().compareTo(BigDecimal.ZERO) > 0 && entry.getAmount() != null) {
                entry.setPricePerLiter(entry.getAmount().divide(entry.getLiters(), 3, RoundingMode.HALF_UP));
            }

            if (entry.getLiters() != null) {
                litersSinceFull = litersSinceFull.add(entry.getLiters());
            }
            boolean measurePoint = anyFullTank ? entry.isFullTank() : true;
            if (measurePoint && entry.getOdometer() != null) {
                if (lastFull != null) {
                    BigDecimal dist = entry.getOdometer().subtract(lastFull.getOdometer());
                    if (dist.compareTo(BigDecimal.ZERO) > 0 && litersSinceFull.compareTo(BigDecimal.ZERO) > 0) {
                        entry.setConsumption(litersSinceFull
                                .divide(dist, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP));
                        attributedLiters = attributedLiters.add(litersSinceFull);
                        attributedDistance = attributedDistance.add(dist);
                    }
                }
                lastFull = entry;
                litersSinceFull = BigDecimal.ZERO;
            }
            previous = entry;
        }
        return new BigDecimal[]{attributedLiters, attributedDistance};
    }
}
