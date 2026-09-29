package com.cuenti.app.api.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {
    private String username;
    private String password;
    /** Authenticator or recovery code, required when the account has two-factor sign-in on. */
    private String code;
    /** Ask for a short-lived access token plus a refresh token instead of one long-lived token. */
    private Boolean refresh;

    /** Absent in requests from older clients; primitive would fail to deserialize then. */
    public boolean isRefresh() {
        return Boolean.TRUE.equals(refresh);
    }
}
