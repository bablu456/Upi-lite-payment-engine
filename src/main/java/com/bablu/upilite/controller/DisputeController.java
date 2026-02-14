package com.bablu.upilite.controller;

import com.bablu.upilite.dto.CreateDisputeRequestDto;
import com.bablu.upilite.dto.DisputeResponseDto;
import com.bablu.upilite.dto.ResolveDisputeRequestDto;
import com.bablu.upilite.service.AuditLogService;
import com.bablu.upilite.service.DisputeService;
import com.bablu.upilite.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;
    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;

    @Value("${app.rate-limit.dispute-raise.max-attempts:8}")
    private int disputeRaiseMaxAttempts;

    @Value("${app.rate-limit.dispute-raise.window-seconds:600}")
    private long disputeRaiseWindowSeconds;

    @PostMapping
    public ResponseEntity<DisputeResponseDto> raiseDispute(@RequestBody CreateDisputeRequestDto request,
                                                           HttpServletRequest servletRequest,
                                                           Authentication authentication) {
        String key = authentication.getName() + "|" + servletRequest.getRemoteAddr();
        rateLimitService.assertAllowed(
                "DISPUTE_RAISE",
                key,
                disputeRaiseMaxAttempts,
                Duration.ofSeconds(disputeRaiseWindowSeconds)
        );

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("transactionId", request == null ? null : request.getTransactionId());
        auditDetails.put("reason", request == null ? null : request.getReason());

        try {
            DisputeResponseDto response = disputeService.raiseDispute(authentication.getName(), request);
            auditLogService.logSuccess("DISPUTE_RAISE", authentication.getName(), servletRequest.getRequestURI(), auditDetails);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            auditLogService.logFailure("DISPUTE_RAISE", authentication.getName(), servletRequest.getRequestURI(), auditDetails, exception);
            throw exception;
        }
    }

    @GetMapping
    public ResponseEntity<List<DisputeResponseDto>> getMyDisputes(Authentication authentication,
                                                                  @RequestParam(required = false) String status) {
        List<DisputeResponseDto> response = disputeService.getMyDisputes(authentication.getName(), status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeResponseDto> getMyDisputeById(@PathVariable UUID disputeId,
                                                               Authentication authentication) {
        DisputeResponseDto response = disputeService.getMyDisputeById(authentication.getName(), disputeId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{disputeId}/under-review")
    public ResponseEntity<DisputeResponseDto> markUnderReview(@PathVariable UUID disputeId,
                                                              HttpServletRequest servletRequest,
                                                              Authentication authentication) {
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("disputeId", disputeId);

        try {
            DisputeResponseDto response = disputeService.markUnderReview(authentication.getName(), disputeId);
            auditLogService.logSuccess("DISPUTE_UNDER_REVIEW", authentication.getName(), servletRequest.getRequestURI(), auditDetails);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            auditLogService.logFailure("DISPUTE_UNDER_REVIEW", authentication.getName(), servletRequest.getRequestURI(), auditDetails, exception);
            throw exception;
        }
    }

    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<DisputeResponseDto> resolveDispute(@PathVariable UUID disputeId,
                                                             @RequestBody(required = false) ResolveDisputeRequestDto request,
                                                             HttpServletRequest servletRequest,
                                                             Authentication authentication) {
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("disputeId", disputeId);
        auditDetails.put("issueRefund", request == null ? null : request.getIssueRefund());

        try {
            DisputeResponseDto response = disputeService.resolveDispute(authentication.getName(), disputeId, request);
            auditLogService.logSuccess("DISPUTE_RESOLVE", authentication.getName(), servletRequest.getRequestURI(), auditDetails);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            auditLogService.logFailure("DISPUTE_RESOLVE", authentication.getName(), servletRequest.getRequestURI(), auditDetails, exception);
            throw exception;
        }
    }
}
