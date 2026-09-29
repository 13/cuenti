package com.cuenti.app.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * How long a signed-in web UI user may stay inactive before being signed out
 * ({@code cuenti.security.idle-timeout}, env {@code IDLE_TIMEOUT}). Zero or
 * negative disables idle sign-out.
 * <p>
 * The browser enforces the timeout (warning dialog, then redirect to the login
 * page); {@link IdleSessionFilter} is the server-side backstop and allows
 * {@link #SERVER_GRACE} extra so it never fires before the browser does.
 */
@Component
public class IdleTimeoutSettings {

    /** How long before sign-out the warning dialog appears (capped at half the timeout). */
    static final Duration WARNING = Duration.ofSeconds(60);

    /** The browser only reports activity to the server this often, so the server allows this much slack. */
    static final Duration SERVER_GRACE = Duration.ofSeconds(60);

    private final Duration timeout;

    public IdleTimeoutSettings(@Value("${cuenti.security.idle-timeout:15m}") Duration timeout) {
        this.timeout = timeout;
    }

    public boolean isEnabled() {
        return !timeout.isZero() && !timeout.isNegative();
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Duration getWarning() {
        Duration half = timeout.dividedBy(2);
        return half.compareTo(WARNING) < 0 ? half : WARNING;
    }

    Duration getServerTimeout() {
        return timeout.plus(SERVER_GRACE);
    }
}
