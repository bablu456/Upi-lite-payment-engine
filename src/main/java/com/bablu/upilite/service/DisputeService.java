package com.bablu.upilite.service;

import com.bablu.upilite.dto.CreateDisputeRequestDto;
import com.bablu.upilite.dto.DisputeResponseDto;
import com.bablu.upilite.dto.DisputeTimelineEventDto;
import com.bablu.upilite.dto.ResolveDisputeRequestDto;
import com.bablu.upilite.entity.DisputeCase;
import com.bablu.upilite.entity.DisputeStatus;
import com.bablu.upilite.entity.LedgerEntry;
import com.bablu.upilite.entity.LedgerEntryType;
import com.bablu.upilite.entity.LedgerSourceType;
import com.bablu.upilite.entity.Transaction;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.exception.DisputeNotFoundException;
import com.bablu.upilite.exception.InvalidTransferRequestException;
import com.bablu.upilite.exception.WalletLimitExceededException;
import com.bablu.upilite.repository.DisputeCaseRepository;
import com.bablu.upilite.repository.LedgerEntryRepository;
import com.bablu.upilite.repository.TransactionRepository;
import com.bablu.upilite.repository.WalletRepository;
import com.bablu.upilite.util.PaymentPolicyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisputeService {

    private final UserService userService;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final DisputeCaseRepository disputeCaseRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public DisputeResponseDto raiseDispute(String userEmail, CreateDisputeRequestDto request) {
        validateCreateDisputeRequest(request);

        User user = userService.getUserByEmailOrThrow(userEmail);
        Wallet userWallet = userService.resolveWalletForUser(user);
        Transaction transaction = getTransactionOrThrow(request.getTransactionId());

        ensureUserBelongsToTransaction(userWallet, transaction);

        boolean alreadyRaised = disputeCaseRepository.existsByTransactionIdAndRaisedByUserIdAndStatusNot(
                transaction.getId(),
                user.getId(),
                DisputeStatus.RESOLVED
        );
        if (alreadyRaised) {
            throw new InvalidTransferRequestException("An active dispute already exists for this transaction.");
        }

        DisputeCase disputeCase = DisputeCase.builder()
                .transactionId(transaction.getId())
                .raisedByUserId(user.getId())
                .reason(request.getReason().trim())
                .description(normalizeOptionalText(request.getDescription(), 500))
                .status(DisputeStatus.OPEN)
                .build();

        return toDisputeResponse(disputeCaseRepository.save(disputeCase));
    }

    public List<DisputeResponseDto> getMyDisputes(String userEmail, String status) {
        User user = userService.getUserByEmailOrThrow(userEmail);
        List<DisputeCase> disputes;

        if (!StringUtils.hasText(status)) {
            disputes = disputeCaseRepository.findByRaisedByUserIdOrderByCreatedAtDesc(user.getId());
        } else {
            DisputeStatus disputeStatus;
            try {
                disputeStatus = DisputeStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new InvalidTransferRequestException("status must be one of OPEN, UNDER_REVIEW, RESOLVED.");
            }
            disputes = disputeCaseRepository.findByRaisedByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), disputeStatus);
        }

        return disputes.stream()
                .map(this::toDisputeResponse)
                .toList();
    }

    public DisputeResponseDto getMyDisputeById(String userEmail, UUID disputeId) {
        User user = userService.getUserByEmailOrThrow(userEmail);
        DisputeCase disputeCase = findDisputeByIdAndUser(disputeId, user.getId());
        return toDisputeResponse(disputeCase);
    }

    @Transactional
    public DisputeResponseDto markUnderReview(String userEmail, UUID disputeId) {
        User user = userService.getUserByEmailOrThrow(userEmail);
        DisputeCase disputeCase = findDisputeByIdAndUser(disputeId, user.getId());

        if (disputeCase.getStatus() == DisputeStatus.RESOLVED) {
            throw new InvalidTransferRequestException("Resolved disputes cannot be moved back to review.");
        }

        disputeCase.setStatus(DisputeStatus.UNDER_REVIEW);
        if (disputeCase.getUnderReviewAt() == null) {
            disputeCase.setUnderReviewAt(LocalDateTime.now());
        }

        return toDisputeResponse(disputeCaseRepository.save(disputeCase));
    }

    @Transactional
    public DisputeResponseDto resolveDispute(String userEmail, UUID disputeId, ResolveDisputeRequestDto request) {
        User user = userService.getUserByEmailOrThrow(userEmail);
        DisputeCase disputeCase = findDisputeByIdAndUser(disputeId, user.getId());

        if (disputeCase.getStatus() == DisputeStatus.RESOLVED) {
            throw new InvalidTransferRequestException("Dispute is already resolved.");
        }

        disputeCase.setStatus(DisputeStatus.RESOLVED);
        disputeCase.setResolvedAt(LocalDateTime.now());
        disputeCase.setResolutionNote(normalizeOptionalText(request == null ? null : request.getResolutionNote(), 250));

        if (Boolean.TRUE.equals(request == null ? null : request.getIssueRefund())) {
            processRefund(disputeCase, user);
        }

        return toDisputeResponse(disputeCaseRepository.save(disputeCase));
    }

    private void processRefund(DisputeCase disputeCase, User user) {
        if (Boolean.TRUE.equals(disputeCase.getRefundProcessed())) {
            return;
        }

        Transaction transaction = getTransactionOrThrow(disputeCase.getTransactionId());
        Wallet userWallet = userService.resolveWalletForUser(user);

        if (!userWallet.getId().equals(transaction.getSenderId())) {
            throw new InvalidTransferRequestException(
                    "Refund simulation is available only for debit transactions raised by sender.");
        }

        BigDecimal amount = transaction.getAmount();
        BigDecimal updatedBalance = userWallet.getBalance().add(amount);
        if (updatedBalance.compareTo(PaymentPolicyConstants.MAX_WALLET_BALANCE) > 0) {
            throw new WalletLimitExceededException("Refund exceeds wallet limit of Rs 2000.");
        }

        userWallet.setBalance(updatedBalance);
        walletRepository.save(userWallet);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .walletId(userWallet.getId())
                .transactionId(transaction.getId())
                .entryType(LedgerEntryType.CREDIT)
                .sourceType(LedgerSourceType.DISPUTE_REFUND)
                .amount(amount)
                .balanceAfter(updatedBalance)
                .narration("Dispute refund for transaction " + transaction.getId())
                .build());

        disputeCase.setRefundProcessed(true);
        disputeCase.setRefundAmount(amount);
    }

    private DisputeCase findDisputeByIdAndUser(UUID disputeId, UUID userId) {
        if (disputeId == null) {
            throw new InvalidTransferRequestException("disputeId is required.");
        }

        return disputeCaseRepository.findByIdAndRaisedByUserId(disputeId, userId)
                .orElseThrow(() -> new DisputeNotFoundException("Dispute case not found."));
    }

    private Transaction getTransactionOrThrow(UUID transactionId) {
        if (transactionId == null) {
            throw new InvalidTransferRequestException("transactionId is required.");
        }

        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new InvalidTransferRequestException("Transaction not found for dispute."));
    }

    private void ensureUserBelongsToTransaction(Wallet userWallet, Transaction transaction) {
        UUID walletId = userWallet.getId();
        if (!walletId.equals(transaction.getSenderId()) && !walletId.equals(transaction.getReceiverId())) {
            throw new InvalidTransferRequestException("You can raise dispute only for your own transaction.");
        }
    }

    private void validateCreateDisputeRequest(CreateDisputeRequestDto request) {
        if (request == null) {
            throw new InvalidTransferRequestException("Dispute payload is required.");
        }

        if (request.getTransactionId() == null) {
            throw new InvalidTransferRequestException("transactionId is required.");
        }

        String reason = normalizeOptionalText(request.getReason(), 80);
        if (!StringUtils.hasText(reason)) {
            throw new InvalidTransferRequestException("reason is required.");
        }
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new InvalidTransferRequestException("Text exceeds max length of " + maxLength + " characters.");
        }
        return normalized;
    }

    private DisputeResponseDto toDisputeResponse(DisputeCase disputeCase) {
        return DisputeResponseDto.builder()
                .disputeId(disputeCase.getId())
                .transactionId(disputeCase.getTransactionId())
                .status(disputeCase.getStatus())
                .reason(disputeCase.getReason())
                .description(disputeCase.getDescription())
                .resolutionNote(disputeCase.getResolutionNote())
                .refundProcessed(Boolean.TRUE.equals(disputeCase.getRefundProcessed()))
                .refundAmount(disputeCase.getRefundAmount())
                .createdAt(disputeCase.getCreatedAt())
                .underReviewAt(disputeCase.getUnderReviewAt())
                .resolvedAt(disputeCase.getResolvedAt())
                .updatedAt(disputeCase.getUpdatedAt())
                .timeline(buildTimeline(disputeCase))
                .build();
    }

    private List<DisputeTimelineEventDto> buildTimeline(DisputeCase disputeCase) {
        List<DisputeTimelineEventDto> timeline = new ArrayList<>();

        timeline.add(DisputeTimelineEventDto.builder()
                .status(DisputeStatus.OPEN)
                .title("Case Opened")
                .description("Dispute raised successfully.")
                .occurredAt(disputeCase.getCreatedAt())
                .completed(true)
                .build());

        timeline.add(DisputeTimelineEventDto.builder()
                .status(DisputeStatus.UNDER_REVIEW)
                .title("Under Review")
                .description("Case is being reviewed.")
                .occurredAt(disputeCase.getUnderReviewAt())
                .completed(disputeCase.getStatus() == DisputeStatus.UNDER_REVIEW
                        || disputeCase.getStatus() == DisputeStatus.RESOLVED)
                .build());

        String resolvedDescription = Boolean.TRUE.equals(disputeCase.getRefundProcessed())
                ? "Case resolved with refund."
                : "Case resolved.";

        timeline.add(DisputeTimelineEventDto.builder()
                .status(DisputeStatus.RESOLVED)
                .title("Resolved")
                .description(resolvedDescription)
                .occurredAt(disputeCase.getResolvedAt())
                .completed(disputeCase.getStatus() == DisputeStatus.RESOLVED)
                .build());

        return timeline;
    }
}
