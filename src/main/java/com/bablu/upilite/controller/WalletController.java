package com.bablu.upilite.controller;

import com.bablu.upilite.dto.WalletBalanceResponseDto;
import com.bablu.upilite.dto.WalletCreditRequestDto;
import com.bablu.upilite.service.AuditLogService;
import com.bablu.upilite.service.RateLimitService;
import com.bablu.upilite.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;

    @Value("${app.rate-limit.wallet-credit.max-attempts:12}")
    private int walletCreditMaxAttempts;

    @Value("${app.rate-limit.wallet-credit.window-seconds:60}")
    private long walletCreditWindowSeconds;

    @PostMapping("/credit")
    public ResponseEntity<WalletBalanceResponseDto> creditWallet(@RequestBody WalletCreditRequestDto request,
                                                                 @RequestHeader(value = "Idempotency-Key", required = false)
                                                                 String idempotencyKey,
                                                                 HttpServletRequest servletRequest,
                                                                 Authentication authentication) {
        String key = authentication.getName() + "|" + extractClientIp(servletRequest);
        rateLimitService.assertAllowed(
                "WALLET_CREDIT",
                key,
                walletCreditMaxAttempts,
                Duration.ofSeconds(walletCreditWindowSeconds)
        );

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("amount", request == null ? null : request.getAmount());
        if (StringUtils.hasText(idempotencyKey)) {
            auditDetails.put("idempotencyKey", idempotencyKey.trim());
        }

        try {
            WalletBalanceResponseDto response = walletService.creditWallet(authentication.getName(), request, idempotencyKey);
            auditLogService.logSuccess("WALLET_CREDIT", authentication.getName(), servletRequest.getRequestURI(), auditDetails);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            auditLogService.logFailure("WALLET_CREDIT", authentication.getName(), servletRequest.getRequestURI(), auditDetails, exception);
            throw exception;
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
