package com.cuenti.app.security;

import com.cuenti.app.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes sign-ins, failed sign-ins and sign-outs to the audit log. Only
 * password authentication goes through the authentication manager, so API
 * requests carrying a token are not logged one by one.
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditListener {

    private final AuditService auditService;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        auditService.logSecurity(event.getAuthentication().getName(), "LOGIN", describe(event.getAuthentication()));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        auditService.logSecurity(event.getAuthentication().getName(), "LOGIN_FAILED",
                describe(event.getAuthentication()) + ": " + event.getException().getClass().getSimpleName());
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        auditService.logSecurity(event.getAuthentication().getName(), "LOGOUT", describe(event.getAuthentication()));
    }

    /** Idle sign-out, from the browser guard or the server backstop. */
    public void idleLogout(String username, String source) {
        auditService.logSecurity(username, "IDLE_LOGOUT", source);
    }

    /** "web" or "api" plus the client address. */
    private static String describe(Authentication authentication) {
        HttpServletRequest request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
                ? a.getRequest() : null;
        String channel = request != null && request.getRequestURI().startsWith(request.getContextPath() + "/api/")
                ? "api" : "web";
        String address = authentication.getDetails() instanceof WebAuthenticationDetails d
                ? d.getRemoteAddress()
                : request != null ? request.getRemoteAddr() : null;
        return address != null ? channel + ", " + address : channel;
    }
}
