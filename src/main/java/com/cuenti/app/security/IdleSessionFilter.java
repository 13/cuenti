package com.cuenti.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Server-side idle sign-out for the web UI. An open Vaadin tab keeps its HTTP
 * session alive forever (heartbeat every few minutes, push connection), so the
 * container's session timeout never fires. This filter tracks the last request
 * that stems from the user — anything but heartbeat and push traffic — and
 * invalidates an authenticated session once it has been idle longer than
 * {@link IdleTimeoutSettings#getServerTimeout()}.
 * <p>
 * The browser-side guard in {@code IdleLogoutGuard} normally signs the user out
 * first; this is the backstop for a closed laptop lid, disabled JS, etc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdleSessionFilter extends OncePerRequestFilter {

    static final String LAST_ACTIVITY = IdleSessionFilter.class.getName() + ".lastActivity";

    private final IdleTimeoutSettings settings;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // REST API is stateless (JWT), no session to expire
        return !settings.isEnabled() || request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            long now = System.currentTimeMillis();
            Long last = (Long) session.getAttribute(LAST_ACTIVITY);
            if (last != null && isAuthenticated(session)
                    && now - last > settings.getServerTimeout().toMillis()) {
                log.info("Invalidating idle session after {} s", (now - last) / 1000);
                session.invalidate();
                SecurityContextHolder.clearContext();
            } else if (isUserActivity(request)) {
                session.setAttribute(LAST_ACTIVITY, now);
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Heartbeats and the push channel are sent by the browser on its own, not by the user. */
    static boolean isUserActivity(HttpServletRequest request) {
        String type = request.getParameter("v-r");
        return !"heartbeat".equals(type) && !"push".equals(type);
    }

    private static boolean isAuthenticated(HttpSession session) {
        Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (context instanceof SecurityContext sc) {
            Authentication auth = sc.getAuthentication();
            return auth != null && auth.isAuthenticated();
        }
        return false;
    }
}
