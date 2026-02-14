package com.bablu.upilite.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateDisputeRequestDto {
    private UUID transactionId;
    private String reason;
    private String description;
}
