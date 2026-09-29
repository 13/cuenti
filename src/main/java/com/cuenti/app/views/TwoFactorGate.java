package com.cuenti.app.views;

import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.security.TwoFactorSession;
import com.cuenti.app.service.UserService;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * Keeps a session that signed in with the password but has not passed the
 * second factor on {@link TwoFactorView}: every navigation, to any route, is
 * redirected there before the target view is created.
 */
@SpringComponent
@RequiredArgsConstructor
public class TwoFactorGate implements VaadinServiceInitListener {

    private static final Set<Class<?>> OPEN = Set.of(TwoFactorView.class, LoginView.class, RegisterView.class);

    private final SecurityUtils securityUtils;
    private final UserService userService;

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(e -> e.getUI().addBeforeEnterListener(this::check));
    }

    private void check(BeforeEnterEvent event) {
        if (OPEN.contains(event.getNavigationTarget())) {
            return;
        }
        securityUtils.getAuthenticatedUsername()
                .filter(username -> !TwoFactorSession.isVerified(username))
                .filter(username -> userService.findByUsername(username).isTotpEnabled())
                .ifPresent(username -> event.rerouteTo(TwoFactorView.class));
    }
}
