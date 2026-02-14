package com.bablu.upilite.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ErrorResponseDto {
    private String apiPath;
    private String errorMessage;
    private String errorCode;
    private LocalDateTime errorTime;
    private Object metadata;

    public ErrorResponseDto(String apiPath, String errorMessage, String errorCode, LocalDateTime errorTime) {
        this.apiPath = apiPath;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
        this.errorTime = errorTime;
    }

    public ErrorResponseDto(String apiPath,
                            String errorMessage,
                            String errorCode,
                            LocalDateTime errorTime,
                            Object metadata) {
        this(apiPath, errorMessage, errorCode, errorTime);
        this.metadata = metadata;
    }
}
