package com.bablu.upilite.service;

import com.bablu.upilite.dto.ForgotPasswordResetRequestDto;
import com.bablu.upilite.dto.OtpRequestDto;
import com.bablu.upilite.dto.OtpVerificationRequestDto;
import com.bablu.upilite.entity.OtpPurpose;
import com.bablu.upilite.entity.OtpVerification;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.exception.InvalidTransferRequestException;
import com.bablu.upilite.repository.OtpVerificationRepository;
import com.bablu.upilite.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OtpAuthService {

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpNotificationService otpNotificationService;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void requestLoginOtp(OtpRequestDto request) {
        Optional<User> user = findUserByIdentifierOptional(normalizeIdentifier(request == null ? null : request.getIdentifier()));
        if (user.isEmpty()) {
            return;
        }
        createAndSendOtp(user.get(), OtpPurpose.LOGIN);
    }

    @Transactional
    public void requestPasswordResetOtp(OtpRequestDto request) {
        Optional<User> user = findUserByIdentifierOptional(normalizeIdentifier(request == null ? null : request.getIdentifier()));
        if (user.isEmpty()) {
            return;
        }
        createAndSendOtp(user.get(), OtpPurpose.PASSWORD_RESET);
    }

    @Transactional
    public User verifyLoginOtp(OtpVerificationRequestDto request) {
        String identifier = normalizeIdentifier(request == null ? null : request.getIdentifier());
        String otp = normalizeOtp(request == null ? null : request.getOtp());
        return verifyOtp(identifier, otp, OtpPurpose.LOGIN);
    }

    @Transactional
    public void resetPasswordWithOtp(ForgotPasswordResetRequestDto request) {
        String identifier = normalizeIdentifier(request == null ? null : request.getIdentifier());
        String otp = normalizeOtp(request == null ? null : request.getOtp());
        String newPassword = request == null ? null : request.getNewPassword();
        String confirmPassword = request == null ? null : request.getConfirmPassword();

        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
            throw new InvalidTransferRequestException("New password must be at least 6 characters.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new InvalidTransferRequestException("New password and confirm password do not match.");
        }

        User user = verifyOtp(identifier, otp, OtpPurpose.PASSWORD_RESET);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void createAndSendOtp(User user, OtpPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        otpVerificationRepository.consumeActiveOtps(user.getId(), purpose, now);

        String otp = generateOtp();
        OtpVerification otpVerification = OtpVerification.builder()
                .user(user)
                .purpose(purpose)
                .otpHash(passwordEncoder.encode(otp))
                .expiresAt(now.plusMinutes(otpExpiryMinutes))
                .attemptCount(0)
                .createdAt(now)
                .build();
        otpVerificationRepository.save(otpVerification);

        otpNotificationService.sendOtpEmail(user.getEmail(), user.getName(), otp, purpose);
    }

    private User verifyOtp(String identifier, String otp, OtpPurpose purpose) {
        User user = findUserByIdentifierOptional(identifier)
                .orElseThrow(() -> new InvalidTransferRequestException("Invalid or expired OTP."));

        OtpVerification otpVerification = otpVerificationRepository
                .findTopByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
                .orElseThrow(() -> new InvalidTransferRequestException("Invalid or expired OTP."));

        LocalDateTime now = LocalDateTime.now();
        if (otpVerification.getConsumedAt() != null || otpVerification.getExpiresAt().isBefore(now)) {
            throw new InvalidTransferRequestException("Invalid or expired OTP.");
        }

        if (otpVerification.getAttemptCount() >= maxAttempts) {
            otpVerification.setConsumedAt(now);
            otpVerificationRepository.save(otpVerification);
            throw new InvalidTransferRequestException("OTP attempts exceeded. Please request a new OTP.");
        }

        if (!passwordEncoder.matches(otp, otpVerification.getOtpHash())) {
            otpVerification.setAttemptCount(otpVerification.getAttemptCount() + 1);
            if (otpVerification.getAttemptCount() >= maxAttempts) {
                otpVerification.setConsumedAt(now);
            }
            otpVerificationRepository.save(otpVerification);
            throw new InvalidTransferRequestException("Invalid or expired OTP.");
        }

        otpVerification.setConsumedAt(now);
        otpVerificationRepository.save(otpVerification);
        return user;
    }

    private String generateOtp() {
        int value = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(value);
    }

    private Optional<User> findUserByIdentifierOptional(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return Optional.empty();
        }

        if (identifier.contains("@")) {
            return userRepository.findByEmail(identifier.toLowerCase(Locale.ROOT));
        }

        return userRepository.findByMobile(identifier);
    }

    private String normalizeIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            throw new InvalidTransferRequestException("Identifier (email or mobile) is required.");
        }
        return identifier.trim();
    }

    private String normalizeOtp(String otp) {
        if (!StringUtils.hasText(otp) || !otp.trim().matches("\\d{6}")) {
            throw new InvalidTransferRequestException("Valid 6-digit OTP is required.");
        }
        return otp.trim();
    }
}
