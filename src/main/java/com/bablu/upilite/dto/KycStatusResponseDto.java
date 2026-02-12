package com.bablu.upilite.dto;

import com.bablu.upilite.entity.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusResponseDto {
    private UUID userId;
    private KycStatus kycStatus;
    private String kycDocumentName;
    private LocalDateTime kycSubmittedAt;
    private LocalDateTime kycReviewedAt;
    private String message;
}
