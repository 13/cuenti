package com.cuenti.app.api;

import com.cuenti.app.api.dto.AuthResponse;
import com.cuenti.app.api.dto.LoginRequest;
import com.cuenti.app.api.dto.RegisterRequest;
import com.cuenti.app.model.User;
import com.cuenti.app.security.JwtTokenProvider;
import com.cuenti.app.service.GlobalSettingService;
import com.cuenti.app.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final GlobalSettingService globalSettingService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            UsernamePasswordAuthenticationToken credentials =
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());
            credentials.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpRequest));
            Authentication authentication = authenticationManager.authenticate(credentials);

            User user = userService.findByUsername(authentication.getName());

            if (!user.isApiEnabled() && !globalSettingService.isApiEnabled()) {
                return ResponseEntity.status(403).body("API access is not enabled for this user");
            }
            String token = tokenProvider.generateToken(user.getUsername(), user.getTokenVersion());

            return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .defaultCurrency(user.getDefaultCurrency())
                    .darkMode(user.isDarkMode())
                    .locale(user.getLocale())
                    .apiEnabled(user.isApiEnabled())
                    .roles(user.getRoles())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }
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

            String token = tokenProvider.generateToken(user.getUsername(), user.getTokenVersion());

            return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .defaultCurrency(user.getDefaultCurrency())
                    .darkMode(user.isDarkMode())
                    .locale(user.getLocale())
                    .apiEnabled(user.isApiEnabled())
                    .roles(user.getRoles())
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
