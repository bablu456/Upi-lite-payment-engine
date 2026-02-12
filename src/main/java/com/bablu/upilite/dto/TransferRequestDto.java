package com.bablu.upilite.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequestDto {
    private UUID senderId;
    private String receiverUpiId;
    private String receiverMobile;
    private BigDecimal amount;
    private String pin;
}
