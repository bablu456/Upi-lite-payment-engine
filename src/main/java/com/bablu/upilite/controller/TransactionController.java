package com.bablu.upilite.controller;


import com.bablu.upilite.dto.TransferRequestDto;
import com.bablu.upilite.entity.Transaction;
import com.bablu.upilite.service.AuditLogService;
import com.bablu.upilite.service.RateLimitService;
import com.bablu.upilite.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;
    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;

    @Value("${app.rate-limit.transfer.max-attempts:20}")
    private int transferMaxAttempts;

    @Value("${app.rate-limit.transfer.window-seconds:60}")
    private long transferWindowSeconds;

    @PostMapping("/transfer")
    public ResponseEntity<Transaction> transferMoney(@RequestBody TransferRequestDto request,
                                                     @RequestHeader(value = "Idempotency-Key", required = false)
                                                     String idempotencyKey,
                                                     HttpServletRequest servletRequest,
                                                     Authentication authentication){
        String key = authentication.getName() + "|" + extractClientIp(servletRequest);
        rateLimitService.assertAllowed(
                "TRANSFER",
                key,
                transferMaxAttempts,
                Duration.ofSeconds(transferWindowSeconds)
        );

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("receiverUpiId", request == null ? null : request.getReceiverUpiId());
        auditDetails.put("receiverMobile", request == null ? null : request.getReceiverMobile());
        auditDetails.put("amount", request == null ? null : request.getAmount());
        if (StringUtils.hasText(idempotencyKey)) {
            auditDetails.put("idempotencyKey", idempotencyKey.trim());
        }

        try {
            Transaction transaction = transactionService.transferMoney(request, authentication.getName(), idempotencyKey);
            auditLogService.logSuccess("TRANSFER", authentication.getName(), servletRequest.getRequestURI(), auditDetails);
            return ResponseEntity.ok(transaction);
        } catch (Exception exception) {
            auditLogService.logFailure("TRANSFER", authentication.getName(), servletRequest.getRequestURI(), auditDetails, exception);
            throw exception;
        }
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Transaction>>  getTransactionHistory(@PathVariable UUID userId){
        List<Transaction> history = transactionService.getTransactionHistory(userId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/history/{userId}/paged")
    public ResponseEntity<Page<Transaction>> getTransactionHistoryPaged(@PathVariable UUID userId,
                                                                        @RequestParam(defaultValue = "ALL") String type,
                                                                        @RequestParam(required = false)
                                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                        LocalDate fromDate,
                                                                        @RequestParam(required = false)
                                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                        LocalDate toDate,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "10") int size) {
        Page<Transaction> history = transactionService.getTransactionHistoryPage(userId, type, fromDate, toDate, page, size);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/history")
    public ResponseEntity<Page<Transaction>> getMyTransactionHistory(Authentication authentication,
                                                                     @RequestParam(defaultValue = "ALL") String type,
                                                                     @RequestParam(required = false)
                                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                     LocalDate fromDate,
                                                                     @RequestParam(required = false)
                                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                     LocalDate toDate,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "10") int size) {
        Page<Transaction> history = transactionService.getTransactionHistoryPageForUser(
                authentication.getName(),
                type,
                fromDate,
                toDate,
                page,
                size);
        return ResponseEntity.ok(history);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
