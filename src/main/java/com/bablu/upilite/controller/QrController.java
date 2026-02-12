package com.bablu.upilite.controller;

import com.bablu.upilite.dto.QrCodeResponseDto;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.service.QrService;
import com.bablu.upilite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
public class QrController {

    private final UserService userService;
    private final QrService qrService;

    @GetMapping("/my-upi")
    public ResponseEntity<QrCodeResponseDto> generateMyUpiQr(Authentication authentication,
                                                             @RequestParam(required = false) BigDecimal amount,
                                                             @RequestParam(required = false) String note) {
        User user = userService.getUserByEmailOrThrow(authentication.getName());
        QrCodeResponseDto qrCode = qrService.generateUpiQr(user, amount, note);
        return ResponseEntity.ok(qrCode);
    }
}
