package com.bablu.upilite.service;

import com.bablu.upilite.dto.PaymentAlertResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class RealtimeNotificationService {

    private static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final String PAYMENT_ALERT_EVENT = "payment-alert";
    private static final String CONNECTED_EVENT = "connected";

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userEmail) {
        if (!StringUtils.hasText(userEmail)) {
            throw new IllegalArgumentException("User email is required for notification subscription.");
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersByUser.computeIfAbsent(userEmail, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userEmail, emitter));
        emitter.onTimeout(() -> removeEmitter(userEmail, emitter));
        emitter.onError(error -> removeEmitter(userEmail, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name(CONNECTED_EVENT)
                    .data(Map.of(
                            "message", "Realtime notification channel connected.",
                            "timestamp", LocalDateTime.now().toString())));
        } catch (IOException ioException) {
            removeEmitter(userEmail, emitter);
            throw new RuntimeException("Unable to establish realtime notification channel.", ioException);
        }

        return emitter;
    }

    public void publishToUser(String userEmail, PaymentAlertResponseDto payload) {
        if (!StringUtils.hasText(userEmail) || payload == null) {
            return;
        }

        List<SseEmitter> emitters = emittersByUser.get(userEmail);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(PAYMENT_ALERT_EVENT)
                        .data(payload));
            } catch (IOException ioException) {
                removeEmitter(userEmail, emitter);
                log.debug("Dropped SSE emitter for {} due to send error: {}", userEmail, ioException.getMessage());
            }
        }
    }

    private void removeEmitter(String userEmail, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUser.get(userEmail);
        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userEmail);
        }
    }
}
