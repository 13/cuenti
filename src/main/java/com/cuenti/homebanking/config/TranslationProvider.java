package com.cuenti.homebanking.config;

import com.vaadin.flow.i18n.I18NProvider;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.*;

@Component
public class TranslationProvider implements I18NProvider {

    public static final String BUNDLE_PREFIX = "messages";

    private final Map<Locale, ResourceBundle> bundles = new HashMap<>();

    public TranslationProvider() {
        // Pre-load bundles for supported locales
        List<Locale> locales = Arrays.asList(Locale.GERMAN, Locale.ENGLISH);
        for (Locale locale : locales) {
            try {
                bundles.put(locale, ResourceBundle.getBundle(BUNDLE_PREFIX, locale));
            } catch (MissingResourceException e) {
                // Log error if needed
            }
        }
    }

    @Override
    public List<Locale> getProvidedLocales() {
        return Collections.unmodifiableList(Arrays.asList(Locale.GERMAN, Locale.ENGLISH));
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        if (key == null) return "";

        // Use the simplified locale (e.g., 'de' instead of 'de-DE' if specific not found)
        Locale baseLocale = new Locale(locale.getLanguage());
        ResourceBundle bundle = bundles.get(baseLocale);

        if (bundle == null) {
            bundle = bundles.get(Locale.ENGLISH); // Default fallback
        }

        if (bundle == null || !bundle.containsKey(key)) {
            return "! " + key;
        }

        String value = bundle.getString(key);
        if (params.length > 0) {
            return MessageFormat.format(value, params);
        }
        return value;
    }
}
