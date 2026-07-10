package com.cuenti.app.views.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

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

    /**
     * Success toast with an inline action (e.g. "Undo"). Stays longer than a
     * plain success toast so the user can react.
     */
    public static void successWithAction(String message, String actionLabel, Runnable onAction) {
        Notification n = new Notification();
        n.setDuration(6000);
        n.setPosition(Notification.Position.BOTTOM_END);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        Button action = new Button(actionLabel, e -> {
            onAction.run();
            n.close();
        });
        action.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

        HorizontalLayout layout = new HorizontalLayout(new Span(message), action);
        layout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        n.add(layout);
        n.open();
    }

    /** Neutral toast with an inline action (e.g. "Show"). */
    public static void infoWithAction(String message, String actionLabel, Runnable onAction) {
        Notification n = new Notification();
        n.setDuration(8000);
        n.setPosition(Notification.Position.BOTTOM_END);
        n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);

        Button action = new Button(actionLabel, e -> {
            onAction.run();
            n.close();
        });
        action.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

        HorizontalLayout layout = new HorizontalLayout(new Span(message), action);
        layout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        n.add(layout);
        n.open();
    }

    private static void show(String message, NotificationVariant variant) {
        Notification n = Notification.show(message, DEFAULT_DURATION_MS, Notification.Position.BOTTOM_END);
        n.addThemeVariants(variant);
    }
}
