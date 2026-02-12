package com.bablu.upilite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCodeResponseDto {
    private String upiId;
    private String qrPayload;
    private String qrImageDataUri;
}
