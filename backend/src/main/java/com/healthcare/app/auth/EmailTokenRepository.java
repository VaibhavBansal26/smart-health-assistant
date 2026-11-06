package com.healthcare.app.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    /** Locked so two requests presenting the same link cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailToken> findByTokenHash(String tokenHash);

    Optional<EmailToken> findFirstByUserIdAndTypeOrderByCreatedAtDesc(Long userId, EmailToken.Type type);

    @Modifying
    @Query("update EmailToken t set t.usedAt = :now where t.user.id = :userId and t.type = :type and t.usedAt is null")
    int invalidateUnused(@Param("userId") Long userId, @Param("type") EmailToken.Type type, @Param("now") Instant now);

    @Modifying
    @Query("delete from EmailToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
