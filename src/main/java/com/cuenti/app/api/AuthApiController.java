package com.cuenti.app.api;

import com.cuenti.app.api.dto.AuthResponse;
import com.cuenti.app.api.dto.LoginRequest;
import com.cuenti.app.api.dto.RegisterRequest;
import com.cuenti.app.model.User;
import com.cuenti.app.security.JwtTokenProvider;
import com.cuenti.app.security.RefreshTokenService;
import com.cuenti.app.service.AuditService;
import com.cuenti.app.service.GlobalSettingService;
import com.cuenti.app.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    /** Error codes in 401 bodies that clients act on (the old plain-text bodies stay for other cases). */
    static final String TWO_FACTOR_REQUIRED = "two_factor_required";
    static final String INVALID_CODE = "invalid_code";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final GlobalSettingService globalSettingService;
    private final AuditService auditService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication;
        try {
            UsernamePasswordAuthenticationToken credentials =
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());
            credentials.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpRequest));
            authentication = authenticationManager.authenticate(credentials);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }

        User user = userService.findByUsername(authentication.getName());
        if (!user.isApiEnabled() && !globalSettingService.isApiEnabled()) {
            return ResponseEntity.status(403).body("API access is not enabled for this user");
        }

        // The password alone is not enough when two-factor sign-in is on
        if (user.isTotpEnabled()) {
            if (request.getCode() == null || request.getCode().isBlank()) {
                return ResponseEntity.status(401).body(Map.of("error", TWO_FACTOR_REQUIRED));
            }
            if (!userService.verifySecondFactor(user, request.getCode())) {
                auditService.logSecurity(user.getUsername(), "2FA_FAILED", "api, " + httpRequest.getRemoteAddr());
                return ResponseEntity.status(401).body(Map.of("error", INVALID_CODE));
            }
        }

        return ResponseEntity.ok(response(user, request.isRefresh()));
    }

    /**
     * Exchanges a refresh token for a new access token and the next refresh
     * token. The presented refresh token is used up.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        return refreshTokenService.rotate(body.get("refreshToken"))
                .<ResponseEntity<?>>map(rotation -> ResponseEntity.ok(Map.of(
                        "token", tokenProvider.generateAccessToken(
                                rotation.user().getUsername(), rotation.user().getTokenVersion()),
                        "refreshToken", rotation.refreshToken(),
                        "expiresIn", tokenProvider.getAccessExpirationSeconds())))
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "invalid_refresh_token")));
    }

    /**
     * What the sign-in screen needs to know before anyone has signed in: whether
     * to offer registration. Public, like the rest of /api/auth.
     */
    @GetMapping("/settings")
    public ResponseEntity<?> settings() {
        return ResponseEntity.ok(java.util.Map.of(
                "registrationEnabled", globalSettingService.isRegistrationEnabled()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            if (!globalSettingService.isRegistrationEnabled()) {
                return ResponseEntity.status(403).body("Registration is currently disabled");
            }

            User user = userService.registerUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getFirstName(),
                    request.getLastName());

            return ResponseEntity.ok(response(user, request.isRefresh()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Tokens plus the profile fields the clients show right after signing in. */
    private AuthResponse response(User user, boolean refresh) {
        AuthResponse.AuthResponseBuilder builder = AuthResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .defaultCurrency(user.getDefaultCurrency())
                .darkMode(user.isDarkMode())
                .locale(user.getLocale())
                .apiEnabled(user.isApiEnabled())
                .roles(user.getRoles());
        if (refresh) {
            return builder
                    .token(tokenProvider.generateAccessToken(user.getUsername(), user.getTokenVersion()))
                    .refreshToken(refreshTokenService.issue(user))
                    .expiresIn(tokenProvider.getAccessExpirationSeconds())
                    .build();
        }
        return builder
                .token(tokenProvider.generateToken(user.getUsername(), user.getTokenVersion()))
                .expiresIn(tokenProvider.getExpirationSeconds())
                .build();
    }
}
