package com.cuenti.homebanking.api.dto;

import lombok.*;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {
    private Long id;
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
