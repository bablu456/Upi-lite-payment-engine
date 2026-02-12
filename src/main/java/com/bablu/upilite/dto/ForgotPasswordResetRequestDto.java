package com.bablu.upilite.dto;

import lombok.Data;

@Data
public class ForgotPasswordResetRequestDto {
    private String identifier;
    private String otp;
    private String newPassword;
    private String confirmPassword;
}
