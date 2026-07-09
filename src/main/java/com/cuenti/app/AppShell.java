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
