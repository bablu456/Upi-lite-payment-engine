package com.bablu.upilite.exception;

import com.bablu.upilite.dto.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

@ControllerAdvice //  Ye puri app ka "Watchman" hai
public class GlobalExceptionHandler {

    // 1. Handle User Not Found
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotFoundException(UserNotFoundException exception, WebRequest webRequest) {
        ErrorResponseDto errorDto = new ErrorResponseDto(
                webRequest.getDescription(false),
                exception.getMessage(),
                "USER_NOT_FOUND",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    // 2. Handle Insufficient Balance
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponseDto> handleInsufficientBalanceException(InsufficientBalanceException exception, WebRequest webRequest) {
        ErrorResponseDto errorDto = new ErrorResponseDto(
                webRequest.getDescription(false),
                exception.getMessage(),
                "INSUFFICIENT_FUNDS",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    // 3. Handle Any Other Random Error (Fallback)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGlobalException(Exception exception, WebRequest webRequest) {
        ErrorResponseDto errorDto = new ErrorResponseDto(
                webRequest.getDescription(false),
                exception.getMessage(),
                "INTERNAL_ERROR",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}