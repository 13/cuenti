package com.cuenti.app.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Locale-aware currency formatting shared by all views. Falls back to the
 * locale's default currency symbol when the currency code is unknown.
 */
public final class CurrencyFormat {

    private CurrencyFormat() {}

    public static String format(BigDecimal amount, String currencyCode, Locale locale) {
        if (amount == null) {
            return "";
        }
        NumberFormat formatter = NumberFormat.getCurrencyInstance(locale != null ? locale : Locale.getDefault());
        try {
            formatter.setCurrency(java.util.Currency.getInstance(currencyCode));
        } catch (Exception ignored) {
            // unknown/blank code: keep the locale's default currency
        }
        return formatter.format(amount);
    }
}
