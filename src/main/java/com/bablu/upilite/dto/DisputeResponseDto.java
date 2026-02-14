package com.bablu.upilite.dto;

import com.bablu.upilite.entity.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResponseDto {
    private UUID disputeId;
    private UUID transactionId;
    private DisputeStatus status;
    private String reason;
    private String description;
    private String resolutionNote;
    private boolean refundProcessed;
    private BigDecimal refundAmount;
    private LocalDateTime createdAt;
    private LocalDateTime underReviewAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime updatedAt;
    private List<DisputeTimelineEventDto> timeline;
}
