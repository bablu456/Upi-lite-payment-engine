package com.bablu.upilite.dto;

import com.bablu.upilite.entity.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponseDto {
    private UUID userId;
    private String name;
    private String email;
    private String mobile;
    private String upiId;
    private UUID walletId;
    private BigDecimal balance;
    private boolean pinConfigured;
    private KycStatus kycStatus;
    private String kycDocumentName;
    private LocalDateTime kycSubmittedAt;
    private LocalDateTime kycReviewedAt;
}
