package com.bablu.upilite.dto;

import com.bablu.upilite.entity.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponseDto {
    private UUID userId;
    private String name;
    private String mobile;
    private String upiId;
    private KycStatus kycStatus;
}
