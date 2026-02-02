package com.bablu.upilite.service;

import com.bablu.upilite.dto.UserRegistrationDto;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.repository.UserRepository;
import com.bablu.upilite.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public User registerUser(UserRegistrationDto dto) {

        User user = User.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .password(dto.getPassword())
                .build();

        user = userRepository.save(user);


        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .upiId(dto.getUsername())
                .upiId(dto.getUsername() + "@upi")
                .build();
        walletRepository.save(wallet);

        return user;
    }

}
