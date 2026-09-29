package com.cuenti.app.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One link in a chain of rotating refresh tokens. Only a SHA-256 of the token
 * is stored. Every refresh marks the presented token used and issues the next
 * one in the same family; presenting a used token again means it was copied,
 * and the whole family is revoked.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_user", columnList = "user_id"),
        @Index(name = "idx_refresh_tokens_family", columnList = "family_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Plain id rather than a relation: rows go away with the user (FK on delete cascade). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    /** The user's token version at issue; bumping it revokes refresh tokens too. */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;
}
