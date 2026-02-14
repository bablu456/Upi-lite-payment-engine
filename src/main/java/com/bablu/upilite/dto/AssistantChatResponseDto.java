package com.bablu.upilite.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssistantChatResponseDto {
    private String reply;
    private String provider;
    private String model;
    private boolean fallback;
}
