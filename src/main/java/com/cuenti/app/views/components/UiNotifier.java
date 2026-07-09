package com.cuenti.app.views.components;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * Standard toast notifications so every view uses the same duration,
 * position and severity styling.
 */
public final class UiNotifier {

    private static final int DEFAULT_DURATION_MS = 2500;

    private UiNotifier() {}

    public static void success(String message) {
        show(message, NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        // Errors stay longer so users can actually read them
        Notification n = Notification.show(message, 5000, Notification.Position.BOTTOM_END);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    public static void warning(String message) {
        show(message, NotificationVariant.LUMO_WARNING);
    }

    public static void info(String message) {
        show(message, NotificationVariant.LUMO_CONTRAST);
    }

    private static void show(String message, NotificationVariant variant) {
        Notification n = Notification.show(message, DEFAULT_DURATION_MS, Notification.Position.BOTTOM_END);
        n.addThemeVariants(variant);
    }
}
