package com.cuenti.app.api.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private Boolean refresh;

    /** Absent in requests from older clients; primitive would fail to deserialize then. */
    public boolean isRefresh() {
        return Boolean.TRUE.equals(refresh);
    }
}
