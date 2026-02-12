package com.bablu.upilite.service;

import com.bablu.upilite.dto.WalletBalanceResponseDto;
import com.bablu.upilite.dto.WalletCreditRequestDto;
import com.bablu.upilite.entity.IdempotencyOperation;
import com.bablu.upilite.entity.IdempotencyRecord;
import com.bablu.upilite.entity.LedgerEntry;
import com.bablu.upilite.entity.LedgerEntryType;
import com.bablu.upilite.entity.LedgerSourceType;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.exception.InvalidTransferRequestException;
import com.bablu.upilite.exception.WalletLimitExceededException;
import com.bablu.upilite.repository.IdempotencyRecordRepository;
import com.bablu.upilite.repository.LedgerEntryRepository;
import com.bablu.upilite.repository.WalletRepository;
import com.bablu.upilite.util.PaymentPolicyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserService userService;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public WalletBalanceResponseDto creditWallet(String email, WalletCreditRequestDto request) {
        return creditWallet(email, request, null);
    }

    public WalletBalanceResponseDto creditWallet(String email,
                                                 WalletCreditRequestDto request,
                                                 String idempotencyKey) {
        if (request == null || request.getAmount() == null) {
            throw new InvalidTransferRequestException("Amount is required.");
        }

        BigDecimal amount = request.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferRequestException("Amount must be greater than 0.");
        }

        User user = userService.getUserByEmailOrThrow(email);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = null;
        if (normalizedIdempotencyKey != null) {
            requestHash = hashCreditRequest(request);
            Optional<WalletBalanceResponseDto> existingResult = resolveExistingCreditIfAny(
                    user.getId(),
                    normalizedIdempotencyKey,
                    requestHash);
            if (existingResult.isPresent()) {
                return existingResult.get();
            }
        }

        Wallet wallet = userService.resolveWalletForUser(user);
        BigDecimal updatedBalance = wallet.getBalance().add(amount);

        if (updatedBalance.compareTo(PaymentPolicyConstants.MAX_WALLET_BALANCE) > 0) {
            throw new WalletLimitExceededException("Wallet limit exceeded. Maximum allowed balance is Rs 2000.");
        }

        wallet.setBalance(updatedBalance);
        walletRepository.save(wallet);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .walletId(wallet.getId())
                .entryType(LedgerEntryType.CREDIT)
                .sourceType(LedgerSourceType.WALLET_CREDIT)
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .narration("Wallet credited via /wallet/credit")
                .idempotencyKey(normalizedIdempotencyKey)
                .build());

        WalletBalanceResponseDto response = WalletBalanceResponseDto.builder()
                .walletId(wallet.getId())
                .balance(wallet.getBalance())
                .message("Wallet credited successfully.")
                .build();

        if (normalizedIdempotencyKey != null && requestHash != null) {
            persistCreditIdempotencyRecord(
                    user.getId(),
                    normalizedIdempotencyKey,
                    requestHash,
                    response.getWalletId(),
                    response.getBalance(),
                    response.getMessage());
        }

        return response;
    }

    private Optional<WalletBalanceResponseDto> resolveExistingCreditIfAny(UUID userId,
                                                                           String idempotencyKey,
                                                                           String requestHash) {
        Optional<IdempotencyRecord> existingRecord = idempotencyRecordRepository
                .findByUserIdAndOperationAndIdempotencyKey(userId, IdempotencyOperation.WALLET_CREDIT, idempotencyKey);
        if (existingRecord.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyRecord record = existingRecord.get();
        ensureRequestHashMatches(record, requestHash);
        if (record.getWalletId() == null || record.getBalanceSnapshot() == null) {
            throw new InvalidTransferRequestException("Idempotency record is incomplete for this wallet credit key.");
        }

        return Optional.of(WalletBalanceResponseDto.builder()
                .walletId(record.getWalletId())
                .balance(record.getBalanceSnapshot())
                .message(record.getResponseMessage() == null ? "Wallet credited successfully." : record.getResponseMessage())
                .build());
    }

    private void persistCreditIdempotencyRecord(UUID userId,
                                                String idempotencyKey,
                                                String requestHash,
                                                UUID walletId,
                                                BigDecimal balanceSnapshot,
                                                String responseMessage) {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .userId(userId)
                .operation(IdempotencyOperation.WALLET_CREDIT)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .walletId(walletId)
                .balanceSnapshot(balanceSnapshot)
                .responseMessage(responseMessage)
                .build();

        try {
            idempotencyRecordRepository.save(record);
        } catch (DataIntegrityViolationException integrityViolationException) {
            IdempotencyRecord existingRecord = idempotencyRecordRepository
                    .findByUserIdAndOperationAndIdempotencyKey(userId, IdempotencyOperation.WALLET_CREDIT, idempotencyKey)
                    .orElseThrow(() -> new InvalidTransferRequestException("Duplicate idempotency key conflict."));
            ensureRequestHashMatches(existingRecord, requestHash);
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }

        String normalizedKey = idempotencyKey.trim();
        if (normalizedKey.length() < 8 || normalizedKey.length() > 120) {
            throw new InvalidTransferRequestException("Idempotency-Key must be between 8 and 120 characters.");
        }
        return normalizedKey;
    }

    private String hashCreditRequest(WalletCreditRequestDto request) {
        String amount = request.getAmount() == null
                ? ""
                : request.getAmount().stripTrailingZeros().toPlainString();
        return sha256Hex(amount);
    }

    private void ensureRequestHashMatches(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new InvalidTransferRequestException(
                    "Idempotency-Key already used with different request payload.");
        }
    }

    private String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to compute request hash.", exception);
        }
    }
}
