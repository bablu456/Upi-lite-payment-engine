package com.bablu.upilite.service;

import com.bablu.upilite.dto.PaymentAlertResponseDto;
import com.bablu.upilite.dto.TransferRequestDto;
import com.bablu.upilite.entity.IdempotencyOperation;
import com.bablu.upilite.entity.IdempotencyRecord;
import com.bablu.upilite.entity.LedgerEntry;
import com.bablu.upilite.entity.LedgerEntryType;
import com.bablu.upilite.entity.LedgerSourceType;
import com.bablu.upilite.entity.PaymentStatus;
import com.bablu.upilite.entity.Transaction;
import com.bablu.upilite.entity.TransactionHistoryType;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.exception.InsufficientBalanceException;
import com.bablu.upilite.exception.InvalidPinException;
import com.bablu.upilite.exception.ScamRiskException;
import com.bablu.upilite.exception.InvalidTransferRequestException;
import com.bablu.upilite.exception.UserNotFoundException;
import com.bablu.upilite.exception.WalletLimitExceededException;
import com.bablu.upilite.repository.IdempotencyRecordRepository;
import com.bablu.upilite.repository.LedgerEntryRepository;
import com.bablu.upilite.repository.TransactionRepository;
import com.bablu.upilite.repository.UserRepository;
import com.bablu.upilite.repository.WalletRepository;
import com.bablu.upilite.util.PaymentPolicyConstants;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationProducer notificationProducer;
    private final RealtimeNotificationService realtimeNotificationService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ScamRiskService scamRiskService;

    @Transactional
    public Transaction transferMoney(TransferRequestDto request, String senderEmail) {
        return transferMoney(request, senderEmail, null);
    }

    @Transactional
    public Transaction transferMoney(TransferRequestDto request, String senderEmail, String idempotencyKey) {
        validateTransferRequest(request);

        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new UserNotFoundException("Sender not found."));

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = null;
        if (normalizedIdempotencyKey != null) {
            requestHash = hashTransferRequest(request);
            Optional<Transaction> existingResult = resolveExistingTransferIfAny(
                    sender.getId(),
                    normalizedIdempotencyKey,
                    requestHash);
            if (existingResult.isPresent()) {
                return existingResult.get();
            }
        }

        Wallet senderWallet = userService.resolveWalletForUser(sender);

        enforcePinPolicyIfRequired(sender, request.getPin(), request.getAmount());
        Wallet receiverWallet = resolveReceiverWallet(request);
        enforceScamShield(sender, senderWallet, receiverWallet, request);

        if (senderWallet.getId().equals(receiverWallet.getId())) {
            throw new InvalidTransferRequestException("Sender and receiver cannot be the same wallet.");
        }

        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient Balance! Transfer failed.");
        }

        BigDecimal receiverPostBalance = receiverWallet.getBalance().add(request.getAmount());
        if (receiverPostBalance.compareTo(PaymentPolicyConstants.MAX_WALLET_BALANCE) > 0) {
            throw new WalletLimitExceededException("Transfer exceeds receiver wallet limit of Rs 2000.");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(request.getAmount()));
        receiverWallet.setBalance(receiverPostBalance);

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        Transaction transaction = Transaction.builder()
                .senderId(senderWallet.getId())
                .receiverId(receiverWallet.getId())
                .amount(request.getAmount())
                .status(PaymentStatus.SUCCESS)
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        recordTransferLedgerEntries(savedTransaction, senderWallet, receiverWallet, request, normalizedIdempotencyKey);

        String receiverHandle = StringUtils.hasText(request.getReceiverUpiId())
                ? request.getReceiverUpiId().trim()
                : request.getReceiverMobile().trim();

        if (normalizedIdempotencyKey != null && requestHash != null) {
            persistTransferIdempotencyRecord(
                    sender.getId(),
                    normalizedIdempotencyKey,
                    requestHash,
                    savedTransaction.getId(),
                    senderWallet.getId(),
                    senderWallet.getBalance());
        }

        String message = "Payment Successful! Amount: " + request.getAmount() + " sent to " + receiverHandle;
        publishNotificationAfterCommit(message);
        publishRealtimeAlertsAfterCommit(savedTransaction, sender, receiverWallet.getUser(), receiverHandle);

        return savedTransaction;
    }

    public List<Transaction> getTransactionHistory(UUID userOrWalletId) {
        UUID walletId = resolveWalletId(userOrWalletId);
        return transactionRepository.findBySenderIdOrReceiverIdOrderByTimestampDesc(walletId, walletId);
    }

    public Page<Transaction> getTransactionHistoryPage(UUID userOrWalletId,
                                                       String type,
                                                       LocalDate fromDate,
                                                       LocalDate toDate,
                                                       int page,
                                                       int size) {
        UUID walletId = resolveWalletId(userOrWalletId);
        return getTransactionHistoryPageByWallet(walletId, type, fromDate, toDate, page, size);
    }

    public Page<Transaction> getTransactionHistoryPageForUser(String userEmail,
                                                              String type,
                                                              LocalDate fromDate,
                                                              LocalDate toDate,
                                                              int page,
                                                              int size) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found."));
        Wallet wallet = userService.resolveWalletForUser(user);
        return getTransactionHistoryPageByWallet(wallet.getId(), type, fromDate, toDate, page, size);
    }

    private void validateTransferRequest(TransferRequestDto request) {
        if (request == null) {
            throw new InvalidTransferRequestException("Transfer payload is required.");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferRequestException("Amount must be greater than 0.");
        }

        boolean hasUpi = StringUtils.hasText(request.getReceiverUpiId());
        boolean hasMobile = StringUtils.hasText(request.getReceiverMobile());
        if (hasUpi == hasMobile) {
            throw new InvalidTransferRequestException("Provide either receiverUpiId or receiverMobile.");
        }
    }

    private Wallet resolveReceiverWallet(TransferRequestDto request) {
        if (StringUtils.hasText(request.getReceiverUpiId())) {
            String receiverUpiId = request.getReceiverUpiId().trim();
            return walletRepository.findByUpiId(receiverUpiId)
                    .orElseThrow(() -> new UserNotFoundException("Receiver not found with UPI ID: " + receiverUpiId));
        }

        String receiverMobile = request.getReceiverMobile().trim();
        User receiver = userRepository.findByMobile(receiverMobile)
                .orElseThrow(() -> new UserNotFoundException("Receiver not found with mobile: " + receiverMobile));

        return userService.resolveWalletForUser(receiver);
    }

    private Optional<Transaction> resolveExistingTransferIfAny(UUID userId,
                                                               String idempotencyKey,
                                                               String requestHash) {
        Optional<IdempotencyRecord> existingRecord = idempotencyRecordRepository
                .findByUserIdAndOperationAndIdempotencyKey(userId, IdempotencyOperation.TRANSFER, idempotencyKey);
        if (existingRecord.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyRecord record = existingRecord.get();
        ensureRequestHashMatches(record, requestHash);
        if (record.getTransactionId() == null) {
            throw new InvalidTransferRequestException("Idempotency record is incomplete for this transfer key.");
        }

        Transaction existingTransaction = transactionRepository.findById(record.getTransactionId())
                .orElseThrow(() -> new InvalidTransferRequestException("Referenced transfer transaction not found."));
        return Optional.of(existingTransaction);
    }

    private void persistTransferIdempotencyRecord(UUID userId,
                                                  String idempotencyKey,
                                                  String requestHash,
                                                  UUID transactionId,
                                                  UUID walletId,
                                                  BigDecimal balanceSnapshot) {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .userId(userId)
                .operation(IdempotencyOperation.TRANSFER)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .transactionId(transactionId)
                .walletId(walletId)
                .balanceSnapshot(balanceSnapshot)
                .responseMessage("Transfer completed.")
                .build();

        try {
            idempotencyRecordRepository.save(record);
        } catch (DataIntegrityViolationException integrityViolationException) {
            IdempotencyRecord existingRecord = idempotencyRecordRepository
                    .findByUserIdAndOperationAndIdempotencyKey(userId, IdempotencyOperation.TRANSFER, idempotencyKey)
                    .orElseThrow(() -> new InvalidTransferRequestException("Duplicate idempotency key conflict."));
            ensureRequestHashMatches(existingRecord, requestHash);
        }
    }

    private void recordTransferLedgerEntries(Transaction transaction,
                                             Wallet senderWallet,
                                             Wallet receiverWallet,
                                             TransferRequestDto request,
                                             String idempotencyKey) {
        String receiverHandle = StringUtils.hasText(request.getReceiverUpiId())
                ? request.getReceiverUpiId().trim()
                : request.getReceiverMobile().trim();

        String senderHandle = senderWallet.getUpiId() == null ? senderWallet.getId().toString() : senderWallet.getUpiId();

        LedgerEntry senderEntry = LedgerEntry.builder()
                .walletId(senderWallet.getId())
                .transactionId(transaction.getId())
                .entryType(LedgerEntryType.DEBIT)
                .sourceType(LedgerSourceType.TRANSFER)
                .amount(request.getAmount())
                .balanceAfter(senderWallet.getBalance())
                .narration("Transfer to " + receiverHandle)
                .idempotencyKey(idempotencyKey)
                .build();

        LedgerEntry receiverEntry = LedgerEntry.builder()
                .walletId(receiverWallet.getId())
                .transactionId(transaction.getId())
                .entryType(LedgerEntryType.CREDIT)
                .sourceType(LedgerSourceType.TRANSFER)
                .amount(request.getAmount())
                .balanceAfter(receiverWallet.getBalance())
                .narration("Transfer from " + senderHandle)
                .idempotencyKey(idempotencyKey)
                .build();

        ledgerEntryRepository.save(senderEntry);
        ledgerEntryRepository.save(receiverEntry);
    }

    private void enforcePinPolicyIfRequired(User sender, String rawPin, BigDecimal amount) {
        if (amount.compareTo(PaymentPolicyConstants.PIN_REQUIRED_THRESHOLD) < 0) {
            return;
        }

        if (!StringUtils.hasText(sender.getUpiPinHash())) {
            throw new InvalidPinException("UPI PIN is not configured. Please set your PIN first.");
        }

        if (!StringUtils.hasText(rawPin) || !rawPin.trim().matches("\\d{4}")) {
            throw new InvalidPinException("Valid 4-digit UPI PIN is required for transactions >= Rs 500.");
        }

        if (!passwordEncoder.matches(rawPin.trim(), sender.getUpiPinHash())) {
            throw new InvalidPinException("Invalid UPI PIN.");
        }
    }

    private void enforceScamShield(User sender,
                                   Wallet senderWallet,
                                   Wallet receiverWallet,
                                   TransferRequestDto request) {
        ScamRiskAssessment riskAssessment = scamRiskService.evaluateTransferRisk(
                sender,
                senderWallet,
                receiverWallet.getUser(),
                receiverWallet,
                request);

        if (riskAssessment.action() == ScamRiskAction.ALLOW) {
            return;
        }

        if (riskAssessment.action() == ScamRiskAction.BLOCK) {
            throw new ScamRiskException(
                    "Transfer blocked by Scam Shield due to high fraud risk.",
                    ScamRiskAction.BLOCK,
                    riskAssessment.score(),
                    riskAssessment.reasons());
        }

        boolean riskAcknowledged = Boolean.TRUE.equals(request.getRiskAcknowledged());
        if (!riskAcknowledged) {
            throw new ScamRiskException(
                    "Suspicious payment detected. Review warning and confirm to proceed.",
                    ScamRiskAction.CHALLENGE,
                    riskAssessment.score(),
                    riskAssessment.reasons());
        }
    }

    private void publishNotificationAfterCommit(String message) {
        runAfterCommit(() -> notificationProducer.sendNotificationAsync(message));
    }

    private Page<Transaction> getTransactionHistoryPageByWallet(UUID walletId,
                                                                String type,
                                                                LocalDate fromDate,
                                                                LocalDate toDate,
                                                                int page,
                                                                int size) {
        LocalDateTime fromTimestamp = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toTimestamp = toDate == null ? null : toDate.plusDays(1).atStartOfDay().minusNanos(1);

        if (fromTimestamp != null && toTimestamp != null && fromTimestamp.isAfter(toTimestamp)) {
            throw new InvalidTransferRequestException("fromDate cannot be after toDate.");
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "timestamp"));

        TransactionHistoryType historyType;
        try {
            historyType = TransactionHistoryType.from(type);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new InvalidTransferRequestException("type must be one of ALL, CREDIT, or DEBIT.");
        }

        Specification<Transaction> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (historyType == TransactionHistoryType.CREDIT) {
                predicates.add(criteriaBuilder.equal(root.get("receiverId"), walletId));
            } else if (historyType == TransactionHistoryType.DEBIT) {
                predicates.add(criteriaBuilder.equal(root.get("senderId"), walletId));
            } else {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("senderId"), walletId),
                        criteriaBuilder.equal(root.get("receiverId"), walletId)));
            }

            if (fromTimestamp != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), fromTimestamp));
            }

            if (toTimestamp != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("timestamp"), toTimestamp));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return transactionRepository.findAll(specification, pageable);
    }

    private UUID resolveWalletId(UUID userOrWalletId) {
        return walletRepository.findById(userOrWalletId)
                .map(Wallet::getId)
                .or(() -> walletRepository.findByUserId(userOrWalletId).map(Wallet::getId))
                .orElseThrow(() -> new UserNotFoundException("User or wallet not found."));
    }

    private void publishRealtimeAlertsAfterCommit(Transaction transaction,
                                                  User sender,
                                                  User receiver,
                                                  String receiverHandle) {
        if (transaction == null || sender == null) {
            return;
        }

        PaymentAlertResponseDto senderAlert = PaymentAlertResponseDto.builder()
                .eventType("PAYMENT_SUCCESS")
                .direction("DEBIT")
                .message("Payment successful: Rs " + transaction.getAmount() + " sent to " + receiverHandle + ".")
                .transactionId(transaction.getId())
                .amount(transaction.getAmount())
                .counterparty(receiverHandle)
                .status(String.valueOf(transaction.getStatus()))
                .timestamp(transaction.getTimestamp() == null ? LocalDateTime.now() : transaction.getTimestamp())
                .build();

        runAfterCommit(() -> realtimeNotificationService.publishToUser(sender.getEmail(), senderAlert));

        if (receiver == null || !StringUtils.hasText(receiver.getEmail())) {
            return;
        }

        String senderHandle = StringUtils.hasText(sender.getUpiId())
                ? sender.getUpiId()
                : sender.getEmail();

        PaymentAlertResponseDto receiverAlert = PaymentAlertResponseDto.builder()
                .eventType("PAYMENT_SUCCESS")
                .direction("CREDIT")
                .message("Payment received: Rs " + transaction.getAmount() + " from " + senderHandle + ".")
                .transactionId(transaction.getId())
                .amount(transaction.getAmount())
                .counterparty(senderHandle)
                .status(String.valueOf(transaction.getStatus()))
                .timestamp(transaction.getTimestamp() == null ? LocalDateTime.now() : transaction.getTimestamp())
                .build();

        runAfterCommit(() -> realtimeNotificationService.publishToUser(receiver.getEmail(), receiverAlert));
    }

    private void runAfterCommit(Runnable callback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            callback.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                callback.run();
            }
        });
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

    private String hashTransferRequest(TransferRequestDto request) {
        String receiverUpiId = request.getReceiverUpiId() == null ? "" : request.getReceiverUpiId().trim().toLowerCase();
        String receiverMobile = request.getReceiverMobile() == null ? "" : request.getReceiverMobile().trim();
        String amount = request.getAmount() == null
                ? ""
                : request.getAmount().stripTrailingZeros().toPlainString();
        String pin = request.getPin() == null ? "" : request.getPin().trim();
        String riskAcknowledged = String.valueOf(Boolean.TRUE.equals(request.getRiskAcknowledged()));
        String canonicalPayload = receiverUpiId + "|" + receiverMobile + "|" + amount + "|" + pin + "|" + riskAcknowledged;
        return sha256Hex(canonicalPayload);
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
