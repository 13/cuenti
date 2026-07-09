package com.cuenti.app.config;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Global UI error handler: logs uncaught exceptions from UI listeners and
 * shows a friendly notification instead of Vaadin's internal error banner.
 */
@Component
@Slf4j
public class UiErrorHandlerInitListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(sessionInit ->
            sessionInit.getSession().setErrorHandler(error -> {
                log.error("Unhandled UI exception", error.getThrowable());
                UI ui = UI.getCurrent();
                if (ui != null) {
                    ui.access(() -> {
                        Notification n = Notification.show(
                                ui.getTranslation("error.generic"),
                                5000, Notification.Position.BOTTOM_END);
                        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    });
                }
            })
        );
    }
}
