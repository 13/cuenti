package com.cuenti.app.security;

import com.cuenti.app.model.RefreshToken;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.RefreshTokenRepository;
import com.cuenti.app.repository.UserRepository;
import com.cuenti.app.service.AuditService;
import com.cuenti.app.service.GlobalSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Long-lived, rotating refresh tokens for API clients that sign in with
 * {@code "refresh": true}. Such clients get a short-lived access token and use
 * the refresh token to get the next pair without the password.
 */
@Service
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record Rotation(User user, String refreshToken) {}

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final GlobalSettingService globalSettingService;
    private final AuditService auditService;
    private final long refreshExpirationMs;

    public RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository,
                               GlobalSettingService globalSettingService, AuditService auditService,
                               @Value("${jwt.refresh-expiration:2592000000}") long refreshExpirationMs) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.globalSettingService = globalSettingService;
        this.auditService = auditService;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /** Starts a new chain for a fresh sign-in; returns the raw token (only its digest is stored). */
    @Transactional
    public String issue(User user) {
        return issue(user, UUID.randomUUID().toString());
    }

    private String issue(User user, String familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(digest(raw))
                .familyId(familyId)
                .tokenVersion(user.getTokenVersion())
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000))
                .build());
        return raw;
    }

    /**
     * Exchanges a refresh token for its successor. Empty if the token is
     * unknown, expired, already used (then the whole chain is revoked) or the
     * user may no longer use the API.
     */
    @Transactional(noRollbackFor = Exception.class)
    public Optional<Rotation> rotate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> found = repository.findByTokenHash(digest(raw));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = found.get();
        Optional<User> user = userRepository.findById(token.getUserId());

        if (token.getUsedAt() != null) {
            // Only possible if the token was copied: the legitimate client already
            // moved on to the successor. Neither copy may continue.
            log.warn("Refresh token reuse for user id {}, revoking its chain", token.getUserId());
            repository.deleteFamily(token.getFamilyId());
            auditService.logSecurity(user.map(User::getUsername).orElse(null), "REFRESH_REUSE",
                    "api: refresh token presented twice, chain revoked");
            return Optional.empty();
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now()) || user.isEmpty() || !mayUseApi(user.get(), token)) {
            repository.deleteFamily(token.getFamilyId());
            return Optional.empty();
        }

        token.setUsedAt(LocalDateTime.now());
        return Optional.of(new Rotation(user.get(), issue(user.get(), token.getFamilyId())));
    }

    /** Signs out every device of the user that uses refresh tokens. */
    @Transactional
    public void revokeAll(User user) {
        repository.deleteAllForUser(user.getId());
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpired(LocalDateTime.now());
        if (removed > 0) {
            log.info("Removed {} expired refresh tokens", removed);
        }
    }

    private boolean mayUseApi(User user, RefreshToken token) {
        return Boolean.TRUE.equals(user.getEnabled())
                && token.getTokenVersion() == user.getTokenVersion()
                && (user.isApiEnabled() || globalSettingService.isApiEnabled());
    }

    static String digest(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
