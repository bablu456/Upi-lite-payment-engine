package com.bablu.upilite.service;

import com.bablu.upilite.dto.UserRegistrationDto;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.repository.UserRepository;
import com.bablu.upilite.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(UserRegistrationDto dto) {
        // Check if user already exists
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Generate UPI ID from username
        String generatedUpiId = dto.getUsername().toLowerCase().replaceAll("\\s+", "") + "@upilite";

        // Generate a placeholder mobile number (user can update later)
        String placeholderMobile = "91" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 10);

        User user = User.builder()
                .name(dto.getUsername())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword())) // Encode password
                .upiId(generatedUpiId)
                .mobile(placeholderMobile)
                .build();

        user = userRepository.save(user);

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(new BigDecimal(1000))
                .upiId(generatedUpiId)
                .build();
        walletRepository.save(wallet);

        return user;
    }
}
