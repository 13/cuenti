package com.cuenti.homebanking.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Utility class for security-related operations.
 */
public class SecurityUtil {

    private SecurityUtil() {
        // Utility class
    }

    /**
     * Get the username of the currently authenticated user.
     *
     * @return Optional containing the username, or empty if not authenticated
     */
    public static Optional<String> getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
            !"anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.of(authentication.getName());
        }
        return Optional.empty();
    }
}
