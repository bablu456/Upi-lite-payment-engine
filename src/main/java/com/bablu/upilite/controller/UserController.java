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
import com.bablu.upilite.dto.UserProfileResponseDto;
import com.bablu.upilite.dto.UserRegistrationDto;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.repository.UserRepository;
import com.bablu.upilite.service.JwtService;
import com.bablu.upilite.service.OtpAuthService;
import com.bablu.upilite.service.UserService;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OtpAuthService otpAuthService;
    private final AuthenticationManager authenticationManager;

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
    public ResponseEntity<Map<String, String>> requestLoginOtp(@RequestBody OtpRequestDto request) {
        otpAuthService.requestLoginOtp(request);
        return ResponseEntity.ok(Map.of(
                "message", "If an account exists, OTP has been sent to the registered email."
        ));
    }

    @PostMapping("/login/otp/verify")
    public ResponseEntity<AuthResponseDto> verifyLoginOtp(@RequestBody OtpVerificationRequestDto request) {
        User user = otpAuthService.verifyLoginOtp(request);
        String token = jwtService.generateToken(user);

        AuthResponseDto response = AuthResponseDto.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .message("Login successful")
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password/forgot/request")
    public ResponseEntity<Map<String, String>> requestForgotPasswordOtp(@RequestBody OtpRequestDto request) {
        otpAuthService.requestPasswordResetOtp(request);
        return ResponseEntity.ok(Map.of(
                "message", "If an account exists, OTP has been sent to the registered email."
        ));
    }

    @PostMapping("/password/forgot/reset")
    public ResponseEntity<Map<String, String>> resetForgottenPassword(@RequestBody ForgotPasswordResetRequestDto request) {
        otpAuthService.resetPasswordWithOtp(request);
        return ResponseEntity.ok(Map.of(
                "message", "Password reset successful. Please login with OTP."
        ));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponseDto> getCurrentUserProfile(Authentication authentication) {
        UserProfileResponseDto profile = userService.getProfile(authentication.getName());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/pin/setup")
    public ResponseEntity<Map<String, Object>> setupUpiPin(@RequestBody SetPinRequestDto request, Authentication authentication) {
        boolean pinConfigured = userService.setupUpiPin(authentication.getName(), request);
        return ResponseEntity.ok(Map.of(
                "message", "UPI PIN configured successfully.",
                "pinConfigured", pinConfigured
        ));
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
}
