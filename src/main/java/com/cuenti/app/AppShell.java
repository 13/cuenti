package com.cuenti.app;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.aura.Aura;

/**
 * Application Shell configuration for Cuenti.
 * Configures icons, manifest, and PWA settings for all devices.
 * Uses the Vaadin Aura design system; app-specific tokens and utilities
 * live in the "cuenti" theme folder.
 */
@PWA(
  name = "Cuenti",
  shortName = "Cuenti",
  offlinePath = "offline.html",
  iconPath = "images/icon.png"
)
@Push(PushMode.AUTOMATIC)
@StyleSheet(Aura.STYLESHEET)
@Theme("cuenti")
public class AppShell implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        // Apply the persisted theme before the first paint. Without this the
        // page renders in the browser's preferred scheme and flips once the
        // server round-trip applies the user's preference (visible flash;
        // the login page stayed in the wrong scheme until then).
        settings.addInlineWithContents(com.vaadin.flow.component.page.Inline.Position.PREPEND,
                "(function(){var m=document.cookie.match(/(?:^|; )cuenti-theme=([^;]*)/);"
                        + "var s=m?m[1]:(matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light');"
                        + "document.documentElement.style.colorScheme=(s==='dark')?'dark':'light';})();",
                com.vaadin.flow.component.page.Inline.Wrapping.JAVASCRIPT);

        // Standard Favicon
        settings.addLink("icon", "images/favicon.ico");
        
        // Modern SVG Favicon
        settings.addLink("icon", "images/favicon.svg");
        
        // Android/Chrome Icon
        settings.addLink("icon", "images/favicon-96x96.png");
        
        // Apple Touch Icon (iOS)
        settings.addLink("apple-touch-icon", "images/apple-touch-icon.png");
        
        // Web Manifest
        settings.addLink("manifest", "images/site.webmanifest");
    }
}
