package com.cuenti.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory login throttle: max {@value #MAX_ATTEMPTS} POSTs to the
 * login endpoint per client IP within a {@value #WINDOW_MINUTES}-minute
 * window. Successful logins are not distinguished — the window is small
 * enough not to bother legitimate users and large enough to blunt
 * credential stuffing on a single node.
 */
@Component
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_ATTEMPTS = 10;
    static final int WINDOW_MINUTES = 15;

    private record Window(Instant start, int count) {}

    private final Map<String, Window> attempts = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().endsWith("/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = clientIp(request);
        Instant now = Instant.now();

        Window window = attempts.merge(ip, new Window(now, 1), (old, ignored) ->
                Duration.between(old.start(), now).toMinutes() >= WINDOW_MINUTES
                        ? new Window(now, 1)
                        : new Window(old.start(), old.count() + 1));

        if (window.count() > MAX_ATTEMPTS) {
            log.warn("Login rate limit exceeded for {}", ip);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(WINDOW_MINUTES * 60));
            response.getWriter().write("Too many login attempts. Try again later.");
            return;
        }

        // opportunistic cleanup of stale windows
        if (attempts.size() > 10_000) {
            attempts.entrySet().removeIf(e ->
                    Duration.between(e.getValue().start(), now).toMinutes() >= WINDOW_MINUTES);
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
