package com.bablu.upilite.repository;

import com.bablu.upilite.entity.OtpPurpose;
import com.bablu.upilite.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    Optional<OtpVerification> findTopByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, OtpPurpose purpose);

    @Modifying
    @Query("""
            UPDATE OtpVerification o
            SET o.consumedAt = :consumedAt
            WHERE o.user.id = :userId
              AND o.purpose = :purpose
              AND o.consumedAt IS NULL
            """)
    int consumeActiveOtps(@Param("userId") UUID userId,
                          @Param("purpose") OtpPurpose purpose,
                          @Param("consumedAt") LocalDateTime consumedAt);
}
