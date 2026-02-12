package com.bablu.upilite.controller;

import com.bablu.upilite.service.JwtService;
import com.bablu.upilite.service.RealtimeNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final RealtimeNotificationService realtimeNotificationService;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@RequestParam(required = false) String token,
                                          Authentication authentication) {
        String userEmail = resolveUserEmail(authentication, token);
        return realtimeNotificationService.subscribe(userEmail);
    }

    private String resolveUserEmail(Authentication authentication, String token) {
        if (authentication != null && StringUtils.hasText(authentication.getName())) {
            return authentication.getName();
        }

        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Missing notification stream token.");
        }

        String rawToken = token.trim().startsWith("Bearer ")
                ? token.trim().substring(7)
                : token.trim();

        try {
            String userEmail = jwtService.extractUsername(rawToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
            if (!jwtService.isTokenValid(rawToken, userDetails)) {
                throw new ResponseStatusException(UNAUTHORIZED, "Invalid notification stream token.");
            }
            return userEmail;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid notification stream token.");
        }
    }
}
