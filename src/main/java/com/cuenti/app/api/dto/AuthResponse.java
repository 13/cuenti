package com.cuenti.app.api.dto;

import lombok.*;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    /** Only for clients that asked for one ({@code "refresh": true}). */
    private String refreshToken;
    /** Lifetime of {@link #token} in seconds. */
    private long expiresIn;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String defaultCurrency;
    private boolean darkMode;
    private String locale;
    private boolean apiEnabled;
    private Set<String> roles;
}
