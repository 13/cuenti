package com.cuenti.app.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.lumo.Lumo;
import jakarta.servlet.http.Cookie;
import java.util.Locale;

public final class ThemePreference {

    private static final String THEME_COOKIE = "cuenti-theme";
    private static final String DARK = "dark";
    private static final String LOCALE_COOKIE = "cuenti-locale";

    private ThemePreference() {}

    public static void applyThemeFromCookie(UI ui) {
        String value = readThemeCookieValue();
        if (value == null) {
            // No stored preference: follow the OS scheme (matches the
            // pre-paint bootstrap script in AppShell)
            ui.getElement().executeJs(
                    "document.documentElement.removeAttribute('theme');"
                    + "document.documentElement.style.colorScheme="
                    + "matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';");
            return;
        }
        applyTheme(ui, isDarkCookieValue(value));
    }

    public static void applyTheme(UI ui, boolean isDark) {
        // Aura resolves light/dark purely via the CSS color-scheme property.
        // The legacy Lumo theme attribute must NOT be set: html[theme=dark]
        // pins Lumo's dark base color (#233348) on the document background,
        // which painted the drawer navy in both schemes.
        ui.getElement().executeJs(
                "document.documentElement.removeAttribute('theme');" +
                "document.documentElement.style.colorScheme = $0;",
                isDark ? Lumo.DARK : Lumo.LIGHT
        );
    }

    public static void persistThemeCookie(UI ui, boolean isDark) {
        String value = THEME_COOKIE + "=" + (isDark ? DARK : Lumo.LIGHT)
                + ";path=/;max-age=31536000;SameSite=Lax";
        ui.getPage().executeJs("document.cookie = $0", value);
    }

    public static void persistLocaleCookie(UI ui, String localeTag) {
        if (localeTag == null || localeTag.isBlank()) {
            localeTag = Locale.ENGLISH.toLanguageTag();
        }
        String value = LOCALE_COOKIE + "=" + localeTag
                + ";path=/;max-age=31536000;SameSite=Lax";
        ui.getPage().executeJs("document.cookie = $0", value);
    }

    /** Returns the stored theme, or null when the user never chose one. */
    private static String readThemeCookieValue() {
        if (VaadinService.getCurrentRequest() == null || VaadinService.getCurrentRequest().getCookies() == null) {
            return null;
        }

        for (Cookie cookie : VaadinService.getCurrentRequest().getCookies()) {
            if (THEME_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private static String readLocaleCookieValue() {
        if (VaadinService.getCurrentRequest() == null || VaadinService.getCurrentRequest().getCookies() == null) {
            return Locale.ENGLISH.toLanguageTag();
        }

        for (Cookie cookie : VaadinService.getCurrentRequest().getCookies()) {
            if (LOCALE_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return Locale.ENGLISH.toLanguageTag();
    }

    public static void applyLocaleFromCookie(UI ui) {
        String tag = readLocaleCookieValue();
        try {
            Locale locale = Locale.forLanguageTag(tag != null ? tag : "en");
            ui.setLocale(locale);
            VaadinSession session = VaadinSession.getCurrent();
            if (session != null) {
                session.setLocale(locale);
            }
        } catch (Exception e) {
            // ignore and keep default
        }
    }

    private static boolean isDarkCookieValue(String value) {
        return DARK.equalsIgnoreCase(value) || Lumo.DARK.equalsIgnoreCase(value);
    }
}

