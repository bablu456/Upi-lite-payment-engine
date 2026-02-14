package com.bablu.upilite.service;

import com.bablu.upilite.dto.TransferRequestDto;
import com.bablu.upilite.entity.KycStatus;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.repository.TransactionRepository;
import com.bablu.upilite.util.PaymentPolicyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScamRiskService {

    private final TransactionRepository transactionRepository;

    public ScamRiskAssessment evaluateTransferRisk(User sender,
                                                   Wallet senderWallet,
                                                   User receiver,
                                                   Wallet receiverWallet,
                                                   TransferRequestDto request) {
        if (sender == null || senderWallet == null || receiver == null || receiverWallet == null || request == null) {
            return ScamRiskAssessment.allow();
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();
        BigDecimal amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount();

        if (amount.compareTo(PaymentPolicyConstants.SCAM_HIGH_AMOUNT_THRESHOLD) >= 0) {
            score += 35;
            reasons.add("High-value transfer for UPI Lite context.");
        }

        if (isFirstTimeBeneficiary(senderWallet, receiverWallet)
                && amount.compareTo(PaymentPolicyConstants.SCAM_FIRST_TIME_BENEFICIARY_AMOUNT_THRESHOLD) >= 0) {
            score += 25;
            reasons.add("First-time transfer to this beneficiary.");
        }

        long recentDebitCount = countRecentDebits(senderWallet);
        if (recentDebitCount >= PaymentPolicyConstants.SCAM_VELOCITY_THRESHOLD) {
            score += 30;
            reasons.add("Multiple rapid outgoing transfers detected in the last 10 minutes.");
        }

        KycStatus receiverKyc = receiver.getKycStatus() == null ? KycStatus.NOT_SUBMITTED : receiver.getKycStatus();
        if (receiverKyc != KycStatus.APPROVED) {
            score += 20;
            reasons.add("Beneficiary KYC is not approved.");
        }

        if (score >= PaymentPolicyConstants.SCAM_BLOCK_THRESHOLD_SCORE) {
            return ScamRiskAssessment.block(score, reasons);
        }

        if (score >= PaymentPolicyConstants.SCAM_CHALLENGE_THRESHOLD_SCORE) {
            return ScamRiskAssessment.challenge(score, reasons);
        }

        return ScamRiskAssessment.allow();
    }

    private boolean isFirstTimeBeneficiary(Wallet senderWallet, Wallet receiverWallet) {
        return !transactionRepository.existsBySenderIdAndReceiverId(senderWallet.getId(), receiverWallet.getId());
    }

    private long countRecentDebits(Wallet senderWallet) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(PaymentPolicyConstants.SCAM_VELOCITY_WINDOW_MINUTES);
        return transactionRepository.countBySenderIdAndTimestampAfter(senderWallet.getId(), windowStart);
    }
}
