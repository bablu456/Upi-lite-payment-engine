package com.bablu.upilite.dto;

import lombok.Data;

@Data
public class OtpVerificationRequestDto {
    private String identifier;
    private String otp;
}
