package com.bablu.upilite.service;

import com.bablu.upilite.dto.TransferRequestDto;
import com.bablu.upilite.entity.Transaction;
import com.bablu.upilite.entity.PaymentStatus;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.repository.TransactionRepository;
import com.bablu.upilite.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Transactional // 🔥 The Shield: Either everything happens, or nothing happens.
    public Transaction transferMoney(TransferRequestDto request) {

        // 1. Fetch Sender Wallet
        Wallet senderWallet = walletRepository.findById(request.getSenderId())
                .orElseThrow(() -> new RuntimeException("Sender wallet not found"));

        // 2. Fetch Receiver Wallet using UPI ID
        Wallet receiverWallet = walletRepository.findByUpiId(request.getReceiverUpiId())
                .orElseThrow(() -> new RuntimeException("Receiver not found with UPI ID: " + request.getReceiverUpiId()));

        // 3. Validation: Check Balance
        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient Balance! Transfer failed.");
        }

        // 4. Perform Transfer (Update Balances)
        senderWallet.setBalance(senderWallet.getBalance().subtract(request.getAmount()));
        receiverWallet.setBalance(receiverWallet.getBalance().add(request.getAmount()));

        // 5. Save Updated Wallets
        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        // 6. Record Transaction History
        Transaction transaction = Transaction.builder()
                .senderId(senderWallet.getId())
                .receiverId(receiverWallet.getId())
                .amount(request.getAmount())
                .status(PaymentStatus.SUCCESS)
                .build();

        return transactionRepository.save(transaction);
    }
}