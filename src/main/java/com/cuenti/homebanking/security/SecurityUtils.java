package com.cuenti.homebanking.security;

import com.vaadin.flow.spring.security.AuthenticationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Utility class for security-related operations.
 * Provides methods to get the current authenticated user.
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final AuthenticationContext authenticationContext;

    /**
     * Get the username of the currently authenticated user.
     *
     * @return Optional containing the username if authenticated
     */
    public Optional<String> getAuthenticatedUsername() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername);
    }

    /**
     * Check if a user is authenticated.
     *
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        return authenticationContext.isAuthenticated();
    }

    /**
     * Logout the current user.
     */
    public void logout() {
        authenticationContext.logout();
    }
}
