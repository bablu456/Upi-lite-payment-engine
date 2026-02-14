package com.bablu.upilite.controller;

import com.bablu.upilite.dto.AuthResponseDto;
import com.bablu.upilite.dto.ContactMessageRequestDto;
import com.bablu.upilite.dto.ContactResponseDto;
import com.bablu.upilite.dto.ForgotPasswordResetRequestDto;
import com.bablu.upilite.dto.KycStatusResponseDto;
import com.bablu.upilite.dto.LoginRequestDto;
import com.bablu.upilite.dto.OtpRequestDto;
import com.bablu.upilite.dto.OtpVerificationRequestDto;
import com.bablu.upilite.dto.SetPinRequestDto;
import com.bablu.upilite.dto.UpdateProfileRequestDto;
import com.bablu.upilite.dto.UserProfileResponseDto;
import com.bablu.upilite.dto.UserRegistrationDto;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.repository.UserRepository;
import com.bablu.upilite.service.JwtService;
import com.bablu.upilite.service.OtpAuthService;
import com.bablu.upilite.service.RateLimitService;
import com.bablu.upilite.service.UserService;
import com.bablu.upilite.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.time.Duration;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OtpAuthService otpAuthService;
    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;
    private final AuthenticationManager authenticationManager;

    @Value("${app.rate-limit.otp-request.max-attempts:5}")
    private int otpRequestMaxAttempts;

    @Value("${app.rate-limit.otp-request.window-seconds:300}")
    private long otpRequestWindowSeconds;

    @Value("${app.rate-limit.otp-verify.max-attempts:10}")
    private int otpVerifyMaxAttempts;

    @Value("${app.rate-limit.otp-verify.window-seconds:300}")
    private long otpVerifyWindowSeconds;

    @Value("${app.rate-limit.password-reset-request.max-attempts:5}")
    private int passwordResetRequestMaxAttempts;

    @Value("${app.rate-limit.password-reset-request.window-seconds:600}")
    private long passwordResetRequestWindowSeconds;

    @Value("${app.rate-limit.password-reset-verify.max-attempts:8}")
    private int passwordResetVerifyMaxAttempts;

    @Value("${app.rate-limit.password-reset-verify.window-seconds:600}")
    private long passwordResetVerifyWindowSeconds;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> registerUser(@RequestBody UserRegistrationDto dto) {
        try {
            User createdUser = userService.registerUser(dto);

            // Generate JWT token for the newly registered user
            String token = jwtService.generateToken(createdUser);

            AuthResponseDto response = AuthResponseDto.builder()
                    .token(token)
                    .email(createdUser.getEmail())
                    .name(createdUser.getName())
                    .message("Registration successful")
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            AuthResponseDto errorResponse = AuthResponseDto.builder()
                    .message("Registration failed: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody LoginRequestDto dto) {
        try {
            String email = dto.getEmail() == null ? "" : dto.getEmail().trim().toLowerCase();
            if (!StringUtils.hasText(email)) {
                throw new RuntimeException("Email is required");
            }

            // Authenticate user with Spring Security
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, dto.getPassword()));

            // Get user from database
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Generate JWT token
            String token = jwtService.generateToken(user);

            AuthResponseDto response = AuthResponseDto.builder()
                    .token(token)
                    .email(user.getEmail())
                    .name(user.getName())
                    .message("Login successful")
                    .build();

            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            AuthResponseDto errorResponse = AuthResponseDto.builder()
                    .message("Invalid email or password")
                    .build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        } catch (Exception e) {
            AuthResponseDto errorResponse = AuthResponseDto.builder()
                    .message("Login failed: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/login/otp/request")
    public ResponseEntity<Map<String, String>> requestLoginOtp(@RequestBody OtpRequestDto request,
                                                               HttpServletRequest servletRequest) {
        String identifier = normalizeIdentifier(request == null ? null : request.getIdentifier());
        String clientKey = buildClientKey(servletRequest, identifier);
        rateLimitService.assertAllowed(
                "OTP_LOGIN_REQUEST",
                clientKey,
                otpRequestMaxAttempts,
                Duration.ofSeconds(otpRequestWindowSeconds)
        );

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("identifier", maskIdentifier(identifier));
        auditDetails.put("clientIp", extractClientIp(servletRequest));

        try {
            otpAuthService.requestLoginOtp(request);
            auditLogService.logSuccess("OTP_LOGIN_REQUEST", null, servletRequest.getRequestURI(), auditDetails);
            return ResponseEntity.ok(Map.of(
                    "message", "If an account exists, OTP has been sent to the registered email."
            ));
        } catch (Exception exception) {
            auditLogService.logFailure("OTP_LOGIN_REQUEST", null, servletRequest.getRequestURI(), auditDetails, exception);
            throw exception;
        }
    }

    @PostMapping("/login/otp/verify")
    public ResponseEntity<AuthResponseDto> verifyLoginOtp(@RequestBody OtpVerificationRequestDto request,
                                                          HttpServletRequest servletRequest) {
        String identifier = normalizeIdentifier(request == null ? null : request.getIdentifier());
        String clientKey = buildClientKey(servletRequest, identifier);
        rateLimitService.assertAllowed(
                "OTP_LOGIN_VERIFY",
                clientKey,
                otpVerifyMaxAttempts,
                Duration.ofSeconds(otpVerifyWindowSeconds)
        );

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("identifier", maskIdentifier(identifier));
        auditDetails.put("clientIp", extractClientIp(servletRequest));

        try {
            User user = otpAuthService.verifyLoginOtp(request);
            String token = jwtService.generateToken(user);

            AuthResponseDto response = AuthResponseDto.builder()
                    .token(token)
                    .email(user.getEmail())
                    .name(user.getName())
                    .message("Login successful")
                    .build();

            auditLogService.logSuccess("OTP_LOGIN_VERIFY", user.getEmail(), servletRequest.getRequestURI(), auditDetails);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            auditLogService.logFailure("OTP_LOGIN_VERIFY", null, servletRequest.getRequestURI(), auditDetails, exception);
            throw exception;
        }
    }

    @PostMapping("/password/forgot/request")
    public ResponseEntity<Map<String, String>> requestForgotPasswordOtp(@RequestBody OtpRequestDto request,
                                                                        HttpServletRequest servletRequest) {
        String identifier = normalizeIdentifier(request == null ? null : request.getIdentifier());
        String clientKey = buildClientKey(servletRequest, identifier);
        rateLimitService.assertAllowed(
                "PASSWORD_RESET_REQUEST",
                clientKey,
                passwordResetRequestMaxAttempts,
                Duration.ofSeconds(passwordResetRequestWindowSeconds)
        );

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("identifier", maskIdentifier(identifier));
        auditDetails.put("clientIp", extractClientIp(servletRequest));

        try {
            otpAuthService.requestPasswordResetOtp(request);
            auditLogService.logSuccess("PASSWORD_RESET_REQUEST", null, servletRequest.getRequestURI(), auditDetails);
            return ResponseEntity.ok(Map.of(
                    "message", "If an account exists, OTP has been sent to the registered email."
            ));
        } catch (Exception exception) {
            auditLogService.logFailure(
                    "PASSWORD_RESET_REQUEST",
                    null,
                    servletRequest.getRequestURI(),
                    auditDetails,
                    exception
            );
            throw exception;
        }
    }

    @PostMapping("/password/forgot/reset")
    public ResponseEntity<Map<String, String>> resetForgottenPassword(@RequestBody ForgotPasswordResetRequestDto request,
                                                                      HttpServletRequest servletRequest) {
        String identifier = normalizeIdentifier(request == null ? null : request.getIdentifier());
        String clientKey = buildClientKey(servletRequest, identifier);
        rateLimitService.assertAllowed(
                "PASSWORD_RESET_VERIFY",
                clientKey,
                passwordResetVerifyMaxAttempts,
                Duration.ofSeconds(passwordResetVerifyWindowSeconds)
        );

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("identifier", maskIdentifier(identifier));
        auditDetails.put("clientIp", extractClientIp(servletRequest));

        try {
            otpAuthService.resetPasswordWithOtp(request);
            auditLogService.logSuccess("PASSWORD_RESET_VERIFY", null, servletRequest.getRequestURI(), auditDetails);
            return ResponseEntity.ok(Map.of(
                    "message", "Password reset successful. Please login with OTP."
            ));
        } catch (Exception exception) {
            auditLogService.logFailure(
                    "PASSWORD_RESET_VERIFY",
                    null,
                    servletRequest.getRequestURI(),
                    auditDetails,
                    exception
            );
            throw exception;
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponseDto> getCurrentUserProfile(Authentication authentication) {
        UserProfileResponseDto profile = userService.getProfile(authentication.getName());
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponseDto> updateCurrentUserProfile(@RequestBody UpdateProfileRequestDto request,
                                                                           HttpServletRequest servletRequest,
                                                                           Authentication authentication) {
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("updatedName", request == null ? null : request.getName());
        auditDetails.put("updatedUpiId", request == null ? null : request.getUpiId());

        try {
            UserProfileResponseDto updatedProfile = userService.updateProfile(authentication.getName(), request);
            auditLogService.logSuccess(
                    "PROFILE_UPDATE",
                    authentication.getName(),
                    servletRequest.getRequestURI(),
                    auditDetails
            );
            return ResponseEntity.ok(updatedProfile);
        } catch (Exception exception) {
            auditLogService.logFailure(
                    "PROFILE_UPDATE",
                    authentication.getName(),
                    servletRequest.getRequestURI(),
                    auditDetails,
                    exception
            );
            throw exception;
        }
    }

    @PostMapping("/pin/setup")
    public ResponseEntity<Map<String, Object>> setupUpiPin(@RequestBody SetPinRequestDto request,
                                                           HttpServletRequest servletRequest,
                                                           Authentication authentication) {
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("pinLength", request == null || request.getPin() == null ? null : request.getPin().trim().length());

        try {
            boolean pinConfigured = userService.setupUpiPin(authentication.getName(), request);
            auditLogService.logSuccess(
                    "PIN_SETUP",
                    authentication.getName(),
                    servletRequest.getRequestURI(),
                    auditDetails
            );
            return ResponseEntity.ok(Map.of(
                    "message", "UPI PIN configured successfully.",
                    "pinConfigured", pinConfigured
            ));
        } catch (Exception exception) {
            auditLogService.logFailure(
                    "PIN_SETUP",
                    authentication.getName(),
                    servletRequest.getRequestURI(),
                    auditDetails,
                    exception
            );
            throw exception;
        }
    }

    @PostMapping(value = "/kyc/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KycStatusResponseDto> submitKyc(@RequestPart("document") MultipartFile document,
                                                          Authentication authentication) {
        KycStatusResponseDto response = userService.submitKyc(authentication.getName(), document);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/kyc/status")
    public ResponseEntity<KycStatusResponseDto> getKycStatus(Authentication authentication) {
        KycStatusResponseDto response = userService.getKycStatus(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/kyc/mock-approve")
    public ResponseEntity<KycStatusResponseDto> mockApproveKyc(Authentication authentication) {
        KycStatusResponseDto response = userService.mockApproveKyc(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/contacts")
    public ResponseEntity<List<ContactResponseDto>> getContacts(Authentication authentication,
                                                                @RequestParam(required = false) String query) {
        List<ContactResponseDto> contacts = userService.getContacts(authentication.getName(), query);
        return ResponseEntity.ok(contacts);
    }

    @PostMapping("/contacts/message")
    public ResponseEntity<Map<String, String>> sendContactMessage(Authentication authentication,
                                                                  @RequestBody ContactMessageRequestDto request) {
        userService.sendContactMessage(authentication.getName(), request);
        return ResponseEntity.ok(Map.of("message", "Message sent successfully."));
    }

    @GetMapping
    public List<User> getAll() {
        return userRepository.findAll();
    }

    private String buildClientKey(HttpServletRequest request, String subject) {
        return extractClientIp(request) + "|" + (StringUtils.hasText(subject) ? subject : "anonymous");
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String normalizeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String maskIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return "anonymous";
        }
        String normalized = value.trim();
        int visibleChars = Math.min(3, normalized.length());
        return normalized.substring(0, visibleChars) + "***";
    }
}
