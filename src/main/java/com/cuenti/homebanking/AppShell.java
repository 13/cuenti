package com.cuenti.homebanking;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Application Shell configuration for Cuenti.
 * Configures icons, manifest, and PWA settings for all devices.
 */
@PWA(name = "Cuenti Homebanking", shortName = "Cuenti", offlinePath = "offline.html")
@Theme(variant = Lumo.DARK)
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
