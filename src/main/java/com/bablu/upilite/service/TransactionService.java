package com.bablu.upilite.service;

import com.bablu.upilite.dto.TransferRequestDto;
import com.bablu.upilite.entity.Transaction;
import com.bablu.upilite.entity.PaymentStatus;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.exception.InsufficientBalanceException;
import com.bablu.upilite.exception.UserNotFoundException;
import com.bablu.upilite.repository.TransactionRepository;
import com.bablu.upilite.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationProducer notificationProducer; // ✅ Add this

    @Transactional //  The Shield: Either everything happens, or nothing happens.
    public Transaction transferMoney(TransferRequestDto request) {

        Wallet senderWallet = walletRepository.findById(request.getSenderId())
                .orElseThrow(() -> new UserNotFoundException("Sender wallet not found"));

        Wallet receiverWallet = walletRepository.findByUpiId(request.getReceiverUpiId())
                .orElseThrow(() -> new UserNotFoundException("Receiver not found with UPI ID: " + request.getReceiverUpiId()));

        // 3. Validation: Check Balance
        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient Balance! Transfer failed.");
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

        Transaction savedTransaction = transactionRepository.save(transaction);

        // 🚀 KAFKA TRIGGER (Async Notification)
        String message = "Payment Successful! Amount: " + request.getAmount() +
                " sent to " + request.getReceiverUpiId();

        notificationProducer.sendNotification(message); // <-- Ye nayi line hai

        return savedTransaction;
    }

    public List<Transaction> getTransactionHistory(UUID useId){
        return transactionRepository.findBySenderIdOrReceiverId(useId, useId);
    }
}