package com.platform.repository;

import com.platform.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    // Lookups are by hash: the raw token is never stored, so it is never queried by.
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates every live token for a user - used when a new reset is requested, and
     * when the password changes by any other route, so an unopened link in an old inbox
     * stops working.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.usedAt IS NULL")
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /** Housekeeping for rows that can no longer reset anything. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
