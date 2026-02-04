package com.bablu.upilite.controller;


import com.bablu.upilite.dto.TransferRequestDto;
import com.bablu.upilite.entity.Transaction;
import com.bablu.upilite.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<?> transferMoney(@RequestBody TransferRequestDto request){
        try{
            Transaction transaction = transactionService.transferMoney(request);
            return ResponseEntity.ok(transaction);
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Transaction>>  getTransactionHistory(@PathVariable UUID userId){
        List<Transaction> history = transactionService.getTransactionHistory(userId);
        return ResponseEntity.ok(history);
    }
}
