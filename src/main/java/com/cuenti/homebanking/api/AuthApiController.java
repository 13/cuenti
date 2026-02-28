package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.AuthResponse;
import com.cuenti.homebanking.api.dto.LoginRequest;
import com.cuenti.homebanking.api.dto.RegisterRequest;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.JwtTokenProvider;
import com.cuenti.homebanking.service.GlobalSettingService;
import com.cuenti.homebanking.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

            String token = tokenProvider.generateToken(authentication);
            User user = userService.findByUsername(request.getUsername());

            if (!user.isApiEnabled() && !globalSettingService.isApiEnabled()) {
                return ResponseEntity.status(403).body("API access is not enabled for this user");
            }

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

            String token = tokenProvider.generateToken(user.getUsername());

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
