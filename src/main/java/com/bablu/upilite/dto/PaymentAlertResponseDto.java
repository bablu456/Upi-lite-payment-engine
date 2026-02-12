package com.bablu.upilite.dto;

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
public class PaymentAlertResponseDto {
    private String eventType;
    private String direction;
    private String message;
    private UUID transactionId;
    private BigDecimal amount;
    private String counterparty;
    private String status;
    private LocalDateTime timestamp;
}
