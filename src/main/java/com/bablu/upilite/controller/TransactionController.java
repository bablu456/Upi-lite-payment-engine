package com.bablu.upilite.controller;


import com.bablu.upilite.dto.TransferRequestDto;
import com.bablu.upilite.entity.Transaction;
import com.bablu.upilite.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<Transaction> transferMoney(@RequestBody TransferRequestDto request,
                                                     @RequestHeader(value = "Idempotency-Key", required = false)
                                                     String idempotencyKey,
                                                     Authentication authentication){
        Transaction transaction = transactionService.transferMoney(request, authentication.getName(), idempotencyKey);
        return ResponseEntity.ok(transaction);
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
}
