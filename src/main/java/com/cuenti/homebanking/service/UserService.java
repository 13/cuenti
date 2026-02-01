package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for user management and authentication.
 * Implements Spring Security's UserDetailsService for authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Load user by username for Spring Security authentication.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(user.getRoles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()))
                .disabled(!user.getEnabled())
                .build();
    }

    /**
     * Register a new user.
     * The first user to register becomes an admin automatically.
     */
    @Transactional
    public User registerUser(String username, String email, String password, 
                            String firstName, String lastName) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Check if this is the first user registering
        boolean isFirstUser = userRepository.count() == 0;

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .enabled(true)
                .roles(new HashSet<>())
                .defaultCurrency("EUR")
                .darkMode(true)
                .locale("de-DE")
                .apiEnabled(false)
                .build();
        
        // All users get ROLE_USER
        user.getRoles().add("ROLE_USER");

        // First user automatically gets ROLE_ADMIN
        if (isFirstUser) {
            user.getRoles().add("ROLE_ADMIN");
            log.info("🎉 First user registered - automatically granted ADMIN privileges: {}", username);
        } else {
            log.info("User registered with standard privileges: {}", username);
        }
        
        User savedUser = userRepository.save(user);
        log.info("User successfully registered: {} (Roles: {})", savedUser.getUsername(), savedUser.getRoles());

        return savedUser;
    }

    /**
     * Find a user by username.
     */
    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * Get all users.
     */
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Save/Update user.
     */
    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Update user information.
     */
    @Transactional
    public void updateUserInfo(User user, String firstName, String lastName, String email) {
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        userRepository.save(user);
    }

    /**
     * Verify old password.
     */
    public boolean checkPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    /**
     * Update user's password.
     */
    @Transactional
    public void updatePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Update user's default currency.
     */
    @Transactional
    public void updateDefaultCurrency(User user, String currencyCode) {
        user.setDefaultCurrency(currencyCode);
        userRepository.save(user);
    }

    /**
     * Update user's dark mode preference.
     */
    @Transactional
    public void updateDarkMode(User user, boolean darkMode) {
        user.setDarkMode(darkMode);
        userRepository.save(user);
    }

    /**
     * Update user's locale.
     */
    @Transactional
    public void updateLocale(User user, String locale) {
        user.setLocale(locale);
        userRepository.save(user);
    }

    /**
     * Update API enabled state.
     */
    @Transactional
    public void updateApiEnabled(User user, boolean enabled) {
        user.setApiEnabled(enabled);
        userRepository.save(user);
    }

    /**
     * Update user enabled state (Admin action).
     */
    @Transactional
    public void setUserEnabled(User user, boolean enabled) {
        user.setEnabled(enabled);
        userRepository.save(user);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
