package com.bablu.upilite.controller;

import com.bablu.upilite.dto.AuthResponseDto;
import com.bablu.upilite.dto.LoginRequestDto;
import com.bablu.upilite.dto.UserRegistrationDto;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.repository.UserRepository;
import com.bablu.upilite.service.JwtService;
import com.bablu.upilite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
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
            // Authenticate user with Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword()));

            // Get user from database
            User user = userRepository.findByEmail(dto.getEmail())
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

    @GetMapping
    public List<User> getAll() {
        return userRepository.findAll();
    }
}
