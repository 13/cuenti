package com.cuenti.app.repository;

import com.cuenti.app.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Locked so two concurrent refreshes with the same token cannot both rotate it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from RefreshToken r where r.familyId = :familyId")
    int deleteFamily(String familyId);

    @Modifying
    @Query("delete from RefreshToken r where r.userId = :userId")
    int deleteAllForUser(Long userId);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :cutoff")
    int deleteExpired(LocalDateTime cutoff);
}
