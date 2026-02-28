package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.DtoMapper;
import com.cuenti.homebanking.api.dto.UserProfileDTO;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.service.GlobalSettingService;
import com.cuenti.homebanking.service.SecurityUtil;
import com.cuenti.homebanking.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserApiController {

    private final UserService userService;
    private final GlobalSettingService globalSettingService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDTO> getProfile() {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        User user = userService.findByUsername(username);
        return ResponseEntity.ok(DtoMapper.toUserProfileDTO(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDTO> updateProfile(@RequestBody UserProfileDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        User user = userService.findByUsername(username);
        userService.updateUserInfo(user, dto.getFirstName(), dto.getLastName(), dto.getEmail());
        return ResponseEntity.ok(DtoMapper.toUserProfileDTO(user));
    }

    @PutMapping("/password")
    public ResponseEntity<?> updatePassword(@RequestBody PasswordChangeRequest request) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        User user = userService.findByUsername(username);
        if (!userService.checkPassword(user, request.getOldPassword())) {
            return ResponseEntity.badRequest().body("Current password is incorrect");
        }

        userService.updatePassword(user, request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/preferences")
    public ResponseEntity<UserProfileDTO> updatePreferences(@RequestBody Map<String, Object> preferences) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        User user = userService.findByUsername(username);

        if (preferences.containsKey("defaultCurrency")) {
            userService.updateDefaultCurrency(user, (String) preferences.get("defaultCurrency"));
        }
        if (preferences.containsKey("darkMode")) {
            userService.updateDarkMode(user, (Boolean) preferences.get("darkMode"));
        }
        if (preferences.containsKey("locale")) {
            userService.updateLocale(user, (String) preferences.get("locale"));
        }
        if (preferences.containsKey("apiEnabled")) {
            userService.updateApiEnabled(user, (Boolean) preferences.get("apiEnabled"));
        }

        // Refresh user
        user = userService.findByUsername(username);
        return ResponseEntity.ok(DtoMapper.toUserProfileDTO(user));
    }

    // Admin endpoints
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserProfileDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.findAll().stream()
                .map(DtoMapper::toUserProfileDTO)
                .collect(Collectors.toList()));
    }

    @PutMapping("/admin/users/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> setUserEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        User user = userService.findAll().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        userService.setUserEnabled(user, body.getOrDefault("enabled", true));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> getAdminSettings() {
        return ResponseEntity.ok(Map.of(
                "registrationEnabled", globalSettingService.isRegistrationEnabled(),
                "apiEnabled", globalSettingService.isApiEnabled()
        ));
    }

    @PutMapping("/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateAdminSettings(@RequestBody Map<String, Boolean> settings) {
        if (settings.containsKey("registrationEnabled")) {
            globalSettingService.setRegistrationEnabled(settings.get("registrationEnabled"));
        }
        if (settings.containsKey("apiEnabled")) {
            globalSettingService.setApiEnabled(settings.get("apiEnabled"));
        }
        return ResponseEntity.ok().build();
    }

    @Data
    public static class PasswordChangeRequest {
        private String oldPassword;
        private String newPassword;
    }
}
