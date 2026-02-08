package com.cuenti.homebanking.services;

import com.cuenti.homebanking.data.*;
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
import java.util.Optional;
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
    private final CurrencyRepository currencyRepository;
    private final AssetRepository assetRepository;

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

        // Create default currencies for the new user
        createDefaultCurrencies(savedUser);

        // Create default assets for the new user
        createDefaultAssets(savedUser);

        return savedUser;
    }

    /**
     * Create default currencies for a new user.
     */
    private void createDefaultCurrencies(User user) {
        log.info("Creating default currencies for user: {}", user.getUsername());

        currencyRepository.save(Currency.builder()
                .user(user).code("EUR").name("Euro").symbol("€")
                .decimalChar(",").fracDigits(2).groupingChar(".").build());

        currencyRepository.save(Currency.builder()
                .user(user).code("USD").name("US Dollar").symbol("$")
                .decimalChar(".").fracDigits(2).groupingChar(",").build());

        currencyRepository.save(Currency.builder()
                .user(user).code("GBP").name("British Pound").symbol("£")
                .decimalChar(".").fracDigits(2).groupingChar(",").build());

        currencyRepository.save(Currency.builder()
                .user(user).code("CHF").name("Swiss Franc").symbol("CHF")
                .decimalChar(".").fracDigits(2).groupingChar(",").build());

        currencyRepository.save(Currency.builder()
                .user(user).code("BTC").name("Bitcoin").symbol("₿")
                .decimalChar(".").fracDigits(8).groupingChar(",").build());

        log.info("Default currencies created for user: {}", user.getUsername());
    }

    /**
     * Create default assets for a new user.
     */
    private void createDefaultAssets(User user) {
        log.info("Creating default assets for user: {}", user.getUsername());

        assetRepository.save(Asset.builder()
                .user(user).symbol("VWCE.DE").name("Vanguard FTSE All-World")
                .type(Asset.AssetType.ETF).currency("EUR").build());

        assetRepository.save(Asset.builder()
                .user(user).symbol("AMZN").name("Amazon.com Inc.")
                .type(Asset.AssetType.STOCK).currency("USD").build());

        assetRepository.save(Asset.builder()
                .user(user).symbol("AMD").name("Advanced Micro Devices Inc.")
                .type(Asset.AssetType.STOCK).currency("USD").build());

        assetRepository.save(Asset.builder()
                .user(user).symbol("BTC-EUR").name("Bitcoin")
                .type(Asset.AssetType.CRYPTO).currency("EUR").build());

        log.info("Default assets created for user: {}", user.getUsername());
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public Optional<User> get(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    /**
     * Alias for save() method for convenience.
     */
    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Change user password after verifying the old password.
     */
    @Transactional
    public void changePassword(User user, String oldPassword, String newPassword) {
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getUsername());
    }

    @Transactional
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    @Transactional
    public void updateUserInfo(User user, String firstName, String lastName, String email) {
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        userRepository.save(user);
    }

    public boolean checkPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    @Transactional
    public void updatePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void updateDefaultCurrency(User user, String currencyCode) {
        user.setDefaultCurrency(currencyCode);
        userRepository.save(user);
    }

    @Transactional
    public void updateDarkMode(User user, boolean darkMode) {
        user.setDarkMode(darkMode);
        userRepository.save(user);
    }

    @Transactional
    public void updateLocale(User user, String locale) {
        user.setLocale(locale);
        userRepository.save(user);
    }

    @Transactional
    public void updateApiEnabled(User user, boolean enabled) {
        user.setApiEnabled(enabled);
        userRepository.save(user);
    }

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

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        log.info("Deleting user: {} (ID: {})", user.getUsername(), userId);
        userRepository.delete(user);
        log.info("User deleted successfully: {}", user.getUsername());
    }

    @Transactional
    public void deleteUser(User user) {
        log.info("Deleting user: {} (ID: {})", user.getUsername(), user.getId());
        userRepository.delete(user);
        log.info("User deleted successfully: {}", user.getUsername());
    }

    public int count() {
        return (int) userRepository.count();
    }

}
