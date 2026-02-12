package com.bablu.upilite.service;

import com.bablu.upilite.dto.KycStatusResponseDto;
import com.bablu.upilite.dto.ContactMessageRequestDto;
import com.bablu.upilite.dto.ContactResponseDto;
import com.bablu.upilite.dto.SetPinRequestDto;
import com.bablu.upilite.dto.UserProfileResponseDto;
import com.bablu.upilite.dto.UserRegistrationDto;
import com.bablu.upilite.entity.KycStatus;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.exception.InvalidTransferRequestException;
import com.bablu.upilite.exception.UserNotFoundException;
import com.bablu.upilite.repository.UserRepository;
import com.bablu.upilite.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    private static final long MAX_KYC_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_KYC_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg");

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(UserRegistrationDto dto) {
        String email = dto.getEmail() == null ? null : dto.getEmail().trim().toLowerCase();
        if (!StringUtils.hasText(email)) {
            throw new InvalidTransferRequestException("Email is required.");
        }

        String mobile = dto.getMobile() == null ? null : dto.getMobile().trim();
        if (!StringUtils.hasText(mobile) || !mobile.matches("\\d{10,15}")) {
            throw new InvalidTransferRequestException("Valid mobile number is required.");
        }

        if (!StringUtils.hasText(dto.getUsername())) {
            throw new InvalidTransferRequestException("Username is required.");
        }

        if (!StringUtils.hasText(dto.getPassword())) {
            throw new InvalidTransferRequestException("Password is required.");
        }

        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepository.existsByMobile(mobile)) {
            throw new RuntimeException("Mobile already registered");
        }

        String generatedUpiId = generateUniqueUpiId(dto.getUsername());

        User user = User.builder()
                .name(dto.getUsername())
                .email(email)
                .password(passwordEncoder.encode(dto.getPassword())) // Encode password
                .upiId(generatedUpiId)
                .mobile(mobile)
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

    public User getUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }

    public UserProfileResponseDto getProfile(String email) {
        User user = getUserByEmailOrThrow(email);
        Wallet wallet = resolveWalletForUser(user);

        return UserProfileResponseDto.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .upiId(user.getUpiId())
                .walletId(wallet.getId())
                .balance(wallet.getBalance())
                .pinConfigured(StringUtils.hasText(user.getUpiPinHash()))
                .kycStatus(resolveKycStatus(user))
                .kycDocumentName(user.getKycDocumentName())
                .kycSubmittedAt(user.getKycSubmittedAt())
                .kycReviewedAt(user.getKycReviewedAt())
                .build();
    }

    public boolean setupUpiPin(String email, SetPinRequestDto request) {
        if (request == null) {
            throw new InvalidTransferRequestException("PIN setup payload is required.");
        }

        String pin = request.getPin() == null ? "" : request.getPin().trim();
        String confirmPin = request.getConfirmPin() == null ? "" : request.getConfirmPin().trim();

        if (!pin.matches("\\d{4}")) {
            throw new InvalidTransferRequestException("UPI PIN must be exactly 4 digits.");
        }

        if (!pin.equals(confirmPin)) {
            throw new InvalidTransferRequestException("PIN and confirm PIN do not match.");
        }

        User user = getUserByEmailOrThrow(email);
        user.setUpiPinHash(passwordEncoder.encode(pin));
        user.setPinConfiguredAt(LocalDateTime.now());
        userRepository.save(user);
        return true;
    }

    public List<ContactResponseDto> getContacts(String requesterEmail, String query) {
        User requester = getUserByEmailOrThrow(requesterEmail);
        String normalizedQuery = query == null ? "" : query.trim();

        return userRepository.searchContacts(normalizedQuery).stream()
                .filter(user -> !user.getId().equals(requester.getId()))
                .map(this::toContactResponseDto)
                .toList();
    }

    public void sendContactMessage(String senderEmail, ContactMessageRequestDto request) {
        if (request == null) {
            throw new InvalidTransferRequestException("Message payload is required.");
        }

        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (!StringUtils.hasText(message)) {
            throw new InvalidTransferRequestException("Message cannot be empty.");
        }
        if (message.length() > 500) {
            throw new InvalidTransferRequestException("Message must be within 500 characters.");
        }

        boolean hasUpi = StringUtils.hasText(request.getReceiverUpiId());
        boolean hasMobile = StringUtils.hasText(request.getReceiverMobile());
        if (hasUpi == hasMobile) {
            throw new InvalidTransferRequestException("Provide either receiverUpiId or receiverMobile.");
        }

        User sender = getUserByEmailOrThrow(senderEmail);
        User receiver = resolveReceiverForMessage(request);

        if (sender.getId().equals(receiver.getId())) {
            throw new InvalidTransferRequestException("Cannot send message to yourself.");
        }

        LOGGER.info("Mock contact message from {} ({}) to {} ({}): {}",
                sender.getName(),
                sender.getUpiId(),
                receiver.getName(),
                receiver.getUpiId(),
                message);
    }

    public KycStatusResponseDto submitKyc(String email, MultipartFile document) {
        if (document == null || document.isEmpty()) {
            throw new InvalidTransferRequestException("KYC document is required.");
        }

        if (document.getSize() > MAX_KYC_FILE_SIZE_BYTES) {
            throw new InvalidTransferRequestException("KYC document size should be less than 5MB.");
        }

        String documentName = validateAndExtractDocumentName(document);

        User user = getUserByEmailOrThrow(email);
        user.setKycStatus(KycStatus.PENDING);
        user.setKycDocumentName(documentName);
        user.setKycSubmittedAt(LocalDateTime.now());
        user.setKycReviewedAt(null);
        userRepository.save(user);

        return toKycStatusResponse(user, "KYC submitted successfully. Status is now PENDING.");
    }

    public KycStatusResponseDto getKycStatus(String email) {
        User user = getUserByEmailOrThrow(email);
        return toKycStatusResponse(user, "KYC status fetched successfully.");
    }

    public KycStatusResponseDto mockApproveKyc(String email) {
        User user = getUserByEmailOrThrow(email);
        KycStatus currentStatus = resolveKycStatus(user);
        user.setKycStatus(currentStatus);

        if (currentStatus == KycStatus.NOT_SUBMITTED) {
            throw new InvalidTransferRequestException("Please submit KYC before approval.");
        }

        user.setKycStatus(KycStatus.APPROVED);
        user.setKycReviewedAt(LocalDateTime.now());
        userRepository.save(user);
        return toKycStatusResponse(user, "KYC approved (mock).");
    }

    public Wallet resolveWalletForUser(User user) {
        if (user.getWallet() != null) {
            return user.getWallet();
        }

        return walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new UserNotFoundException("Wallet not found for user."));
    }

    private KycStatusResponseDto toKycStatusResponse(User user, String message) {
        return KycStatusResponseDto.builder()
                .userId(user.getId())
                .kycStatus(resolveKycStatus(user))
                .kycDocumentName(user.getKycDocumentName())
                .kycSubmittedAt(user.getKycSubmittedAt())
                .kycReviewedAt(user.getKycReviewedAt())
                .message(message)
                .build();
    }

    private ContactResponseDto toContactResponseDto(User user) {
        return ContactResponseDto.builder()
                .userId(user.getId())
                .name(user.getName())
                .mobile(user.getMobile())
                .upiId(user.getUpiId())
                .kycStatus(resolveKycStatus(user))
                .build();
    }

    private User resolveReceiverForMessage(ContactMessageRequestDto request) {
        if (StringUtils.hasText(request.getReceiverUpiId())) {
            String upiId = request.getReceiverUpiId().trim();
            return userRepository.findByUpiId(upiId)
                    .orElseThrow(() -> new UserNotFoundException("Receiver not found with UPI ID: " + upiId));
        }

        String mobile = request.getReceiverMobile().trim();
        return userRepository.findByMobile(mobile)
                .orElseThrow(() -> new UserNotFoundException("Receiver not found with mobile: " + mobile));
    }

    private KycStatus resolveKycStatus(User user) {
        return user.getKycStatus() == null ? KycStatus.NOT_SUBMITTED : user.getKycStatus();
    }

    private String validateAndExtractDocumentName(MultipartFile document) {
        String originalName = document.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            throw new InvalidTransferRequestException("Invalid KYC document name.");
        }

        String cleanedName = originalName.trim();
        int extensionIndex = cleanedName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == cleanedName.length() - 1) {
            throw new InvalidTransferRequestException("KYC document must be in PDF/JPG/JPEG/PNG format.");
        }

        String extension = cleanedName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_KYC_EXTENSIONS.contains(extension)) {
            throw new InvalidTransferRequestException("KYC document must be in PDF/JPG/JPEG/PNG format.");
        }

        return cleanedName;
    }

    private String generateUniqueUpiId(String username) {
        String base = username.toLowerCase().replaceAll("\\s+", "");
        String candidate = base + "@upilite";

        if (!userRepository.existsByUpiId(candidate)) {
            return candidate;
        }

        for (int i = 1; i <= 9999; i++) {
            candidate = base + i + "@upilite";
            if (!userRepository.existsByUpiId(candidate)) {
                return candidate;
            }
        }

        throw new InvalidTransferRequestException("Unable to generate unique UPI ID. Please try again.");
    }
}
