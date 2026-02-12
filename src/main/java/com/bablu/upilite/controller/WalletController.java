package com.bablu.upilite.controller;

import com.bablu.upilite.dto.WalletBalanceResponseDto;
import com.bablu.upilite.dto.WalletCreditRequestDto;
import com.bablu.upilite.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/credit")
    public ResponseEntity<WalletBalanceResponseDto> creditWallet(@RequestBody WalletCreditRequestDto request,
                                                                 @RequestHeader(value = "Idempotency-Key", required = false)
                                                                 String idempotencyKey,
                                                                 Authentication authentication) {
        WalletBalanceResponseDto response = walletService.creditWallet(authentication.getName(), request, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
