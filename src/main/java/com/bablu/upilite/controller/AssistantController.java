package com.bablu.upilite.controller;

import com.bablu.upilite.dto.AssistantChatRequestDto;
import com.bablu.upilite.dto.AssistantChatResponseDto;
import com.bablu.upilite.service.AssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/chat")
    public ResponseEntity<AssistantChatResponseDto> chat(@RequestBody AssistantChatRequestDto request) {
        return ResponseEntity.ok(assistantService.chat(request));
    }
}
