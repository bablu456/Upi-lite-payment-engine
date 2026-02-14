package com.bablu.upilite.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssistantChatRequestDto {
    private String message;
    private List<AssistantMessageDto> history;
}
