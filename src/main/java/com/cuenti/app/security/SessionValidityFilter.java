package com.cuenti.app.security;

import com.cuenti.app.model.User;
import com.cuenti.app.repository.UserRepository;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Ends web sessions whose account was disabled or deleted or whose password
 * changed since sign-in; otherwise they would keep working until they expire.
 * The account is re-checked at most every {@link #CHECK_INTERVAL_MS} per session.
 * The session that changes its own password records the new credentials via
 * {@link #rememberCredentials}, so only the other sessions end.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionValidityFilter extends OncePerRequestFilter {

    static final long CHECK_INTERVAL_MS = 10_000;
    static final String CREDENTIALS = SessionValidityFilter.class.getName() + ".credentials";
    static final String LAST_CHECK = SessionValidityFilter.class.getName() + ".lastCheck";

    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication auth = session != null ? authentication(session) : null;
        if (auth != null) {
            long now = System.currentTimeMillis();
            Long lastCheck = (Long) session.getAttribute(LAST_CHECK);
            if (lastCheck == null || now - lastCheck >= CHECK_INTERVAL_MS) {
                session.setAttribute(LAST_CHECK, now);
                String reason = invalidReason(auth.getName(), session);
                if (reason != null) {
                    log.info("Ending web session of '{}': {}", auth.getName(), reason);
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String invalidReason(String username, HttpSession session) {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            return "account deleted";
        }
        if (!Boolean.TRUE.equals(user.get().getEnabled())) {
            return "account disabled";
        }
        String current = fingerprint(user.get().getPassword());
        Object known = session.getAttribute(CREDENTIALS);
        if (known == null) {
            // first check after sign-in
            session.setAttribute(CREDENTIALS, current);
            return null;
        }
        return known.equals(current) ? null : "password changed";
    }

    /** Called by the session that changed its own password so it survives the change. */
    public static void rememberCredentials(com.vaadin.flow.server.WrappedSession session, String passwordHash) {
        session.setAttribute(CREDENTIALS, fingerprint(passwordHash));
    }

    /** A digest, so the password hash itself is never kept in (possibly persisted) session state. */
    static String fingerprint(String passwordHash) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Authentication authentication(HttpSession session) {
        Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (context instanceof SecurityContext sc) {
            Authentication auth = sc.getAuthentication();
            return auth != null && auth.isAuthenticated() ? auth : null;
        }
        return null;
    }
}
