package com.bablu.upilite.service;

import com.bablu.upilite.entity.AuditLog;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.repository.AuditLogRepository;
import com.bablu.upilite.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final int MAX_DETAILS_LENGTH = 600;
    private static final int MAX_ERROR_LENGTH = 400;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(String action,
                           String actorEmail,
                           String path,
                           Map<String, Object> details) {
        persist(action, "SUCCESS", actorEmail, path, details, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String action,
                           String actorEmail,
                           String path,
                           Map<String, Object> details,
                           Exception exception) {
        persist(action, "FAILED", actorEmail, path, details, exception);
    }

    private void persist(String action,
                         String status,
                         String actorEmail,
                         String path,
                         Map<String, Object> details,
                         Exception exception) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(StringUtils.hasText(action) ? action : "UNKNOWN_ACTION")
                    .status(status)
                    .actorEmail(normalize(actorEmail, 120))
                    .actorUserId(resolveUserId(actorEmail))
                    .requestId(normalize(MDC.get(REQUEST_ID_KEY), 64))
                    .path(normalize(path, 120))
                    .details(serializeDetails(details))
                    .errorMessage(exception == null ? null : normalize(exception.getMessage(), MAX_ERROR_LENGTH))
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception unexpectedException) {
            log.warn("Failed to persist audit log for action {}: {}", action, unexpectedException.getMessage());
        }
    }

    private UUID resolveUserId(String actorEmail) {
        if (!StringUtils.hasText(actorEmail)) {
            return null;
        }

        return userRepository.findByEmail(actorEmail.trim().toLowerCase())
                .map(User::getId)
                .orElse(null);
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }

        try {
            return normalize(objectMapper.writeValueAsString(details), MAX_DETAILS_LENGTH);
        } catch (JsonProcessingException processingException) {
            return normalize(details.toString(), MAX_DETAILS_LENGTH);
        }
    }

    private String normalize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}
