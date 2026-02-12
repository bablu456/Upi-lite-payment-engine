package com.bablu.upilite.exception;

import com.bablu.upilite.dto.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(WalletLimitExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleWalletLimitExceededException(WalletLimitExceededException exception, WebRequest webRequest) {
        ErrorResponseDto errorDto = new ErrorResponseDto(
                webRequest.getDescription(false),
                exception.getMessage(),
                "WALLET_LIMIT_EXCEEDED",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidTransferRequestException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidTransferRequestException(InvalidTransferRequestException exception, WebRequest webRequest) {
        ErrorResponseDto errorDto = new ErrorResponseDto(
                webRequest.getDescription(false),
                exception.getMessage(),
                "INVALID_TRANSFER_REQUEST",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidPinException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidPinException(InvalidPinException exception, WebRequest webRequest) {
        ErrorResponseDto errorDto = new ErrorResponseDto(
                webRequest.getDescription(false),
                exception.getMessage(),
                "INVALID_UPI_PIN",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleUploadSizeExceeded(MaxUploadSizeExceededException exception, WebRequest webRequest) {
        ErrorResponseDto errorDto = new ErrorResponseDto(
                webRequest.getDescription(false),
                "Uploaded file is too large.",
                "UPLOAD_TOO_LARGE",
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
