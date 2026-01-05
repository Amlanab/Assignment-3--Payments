package com.payment.repository;

import com.payment.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < ?1 OR rt.revoked = true")
    void deleteExpiredOrRevokedTokens(LocalDateTime now);
    
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = ?1")
    void revokeAllUserTokens(Long userId);
}

