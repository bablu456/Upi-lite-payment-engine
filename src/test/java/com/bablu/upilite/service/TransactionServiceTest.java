package com.bablu.upilite.service;

import com.bablu.upilite.dto.TransferRequestDto;
import com.bablu.upilite.entity.IdempotencyOperation;
import com.bablu.upilite.entity.IdempotencyRecord;
import com.bablu.upilite.entity.LedgerEntry;
import com.bablu.upilite.entity.PaymentStatus;
import com.bablu.upilite.entity.Transaction;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.exception.InsufficientBalanceException;
import com.bablu.upilite.exception.InvalidPinException;
import com.bablu.upilite.exception.InvalidTransferRequestException;
import com.bablu.upilite.exception.ScamRiskException;
import com.bablu.upilite.exception.WalletLimitExceededException;
import com.bablu.upilite.repository.IdempotencyRecordRepository;
import com.bablu.upilite.repository.LedgerEntryRepository;
import com.bablu.upilite.repository.TransactionRepository;
import com.bablu.upilite.repository.UserRepository;
import com.bablu.upilite.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private NotificationProducer notificationProducer;
    @Mock
    private RealtimeNotificationService realtimeNotificationService;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock
    private ScamRiskService scamRiskService;

    @InjectMocks
    private TransactionService transactionService;

    private User senderUser;
    private Wallet senderWallet;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        senderUser = User.builder()
                .id(UUID.randomUUID())
                .email("sender@upi.test")
                .upiPinHash("hashed-pin")
                .build();

        senderWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .balance(BigDecimal.valueOf(1500))
                .upiId("sender@upilite")
                .build();

        receiverWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .balance(BigDecimal.valueOf(200))
                .upiId("receiver@upilite")
                .build();

        lenient().when(scamRiskService.evaluateTransferRisk(any(), any(), any(), any(), any()))
                .thenReturn(ScamRiskAssessment.allow());
    }

    @Test
    void transferBelow500WithoutPinSucceeds() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(100));

        mockSender();
        when(walletRepository.findByUpiId("receiver@upilite")).thenReturn(Optional.of(receiverWallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        Transaction response = transactionService.transferMoney(request, senderUser.getEmail());

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(senderWallet.getBalance()).isEqualByComparingTo("1400");
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo("300");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void transferAt500WithoutPinFails() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(500));

        mockSender();

        assertThatThrownBy(() -> transactionService.transferMoney(request, senderUser.getEmail()))
                .isInstanceOf(InvalidPinException.class);
    }

    @Test
    void transferAbove500WrongPinFails() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(700));
        request.setPin("1111");

        mockSender();
        when(passwordEncoder.matches("1111", "hashed-pin")).thenReturn(false);

        assertThatThrownBy(() -> transactionService.transferMoney(request, senderUser.getEmail()))
                .isInstanceOf(InvalidPinException.class);
    }

    @Test
    void transferAbove500CorrectPinSucceeds() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(700));
        request.setPin("1234");

        mockSender();
        when(walletRepository.findByUpiId("receiver@upilite")).thenReturn(Optional.of(receiverWallet));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction response = transactionService.transferMoney(request, senderUser.getEmail());

        assertThat(response.getAmount()).isEqualByComparingTo("700");
        assertThat(senderWallet.getBalance()).isEqualByComparingTo("800");
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo("900");
    }

    @Test
    void transferFailsWhenInsufficientBalance() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(200));

        senderWallet.setBalance(BigDecimal.valueOf(100));
        mockSender();
        when(walletRepository.findByUpiId("receiver@upilite")).thenReturn(Optional.of(receiverWallet));

        assertThatThrownBy(() -> transactionService.transferMoney(request, senderUser.getEmail()))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void transferFailsWhenReceiverBalanceExceedsLimit() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(300));

        receiverWallet.setBalance(BigDecimal.valueOf(1900));
        mockSender();
        when(walletRepository.findByUpiId("receiver@upilite")).thenReturn(Optional.of(receiverWallet));

        assertThatThrownBy(() -> transactionService.transferMoney(request, senderUser.getEmail()))
                .isInstanceOf(WalletLimitExceededException.class);
    }

    @Test
    void senderSpoofAttemptIsIgnoredAndJwtSenderIsUsed() {
        TransferRequestDto request = new TransferRequestDto();
        request.setSenderId(UUID.randomUUID());
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(100));

        mockSender();
        when(walletRepository.findByUpiId("receiver@upilite")).thenReturn(Optional.of(receiverWallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.transferMoney(request, senderUser.getEmail());

        verify(walletRepository, never()).findById(any());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getSenderId()).isEqualTo(senderWallet.getId());
    }

    @Test
    void transferByMobileReceiverWorks() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverMobile("919876543210");
        request.setAmount(BigDecimal.valueOf(120));

        User receiverUser = User.builder()
                .id(UUID.randomUUID())
                .mobile("919876543210")
                .build();

        mockSender();
        when(userRepository.findByMobile("919876543210")).thenReturn(Optional.of(receiverUser));
        when(userService.resolveWalletForUser(receiverUser)).thenReturn(receiverWallet);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.transferMoney(request, senderUser.getEmail());

        verify(walletRepository, never()).findByUpiId(anyString());
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo("320");
    }

    @Test
    void transferWithSameIdempotencyKeyReturnsExistingTransaction() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(120));

        Transaction existingTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .senderId(senderWallet.getId())
                .receiverId(receiverWallet.getId())
                .amount(BigDecimal.valueOf(120))
                .status(PaymentStatus.SUCCESS)
                .build();

        String idempotencyKey = "idem-key-12345";
        IdempotencyRecord record = IdempotencyRecord.builder()
                .userId(senderUser.getId())
                .operation(IdempotencyOperation.TRANSFER)
                .idempotencyKey(idempotencyKey)
                .requestHash(hashTransferRequest(request))
                .transactionId(existingTransaction.getId())
                .build();

        when(userRepository.findByEmail(senderUser.getEmail())).thenReturn(Optional.of(senderUser));
        when(idempotencyRecordRepository.findByUserIdAndOperationAndIdempotencyKey(
                senderUser.getId(),
                IdempotencyOperation.TRANSFER,
                idempotencyKey)).thenReturn(Optional.of(record));
        when(transactionRepository.findById(existingTransaction.getId())).thenReturn(Optional.of(existingTransaction));

        Transaction response = transactionService.transferMoney(request, senderUser.getEmail(), idempotencyKey);

        assertThat(response.getId()).isEqualTo(existingTransaction.getId());
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void transferFailsWithScamRiskChallengeWhenNotAcknowledged() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(350));

        mockSender();
        when(walletRepository.findByUpiId("receiver@upilite")).thenReturn(Optional.of(receiverWallet));
        when(scamRiskService.evaluateTransferRisk(any(), any(), any(), any(), any()))
                .thenReturn(ScamRiskAssessment.challenge(62, List.of("First-time transfer to this beneficiary.")));

        assertThatThrownBy(() -> transactionService.transferMoney(request, senderUser.getEmail()))
                .isInstanceOf(ScamRiskException.class)
                .hasMessageContaining("Suspicious payment detected");
    }

    @Test
    void transferChallengeCanProceedWhenAcknowledged() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(350));
        request.setRiskAcknowledged(true);

        mockSender();
        when(walletRepository.findByUpiId("receiver@upilite")).thenReturn(Optional.of(receiverWallet));
        when(scamRiskService.evaluateTransferRisk(any(), any(), any(), any(), any()))
                .thenReturn(ScamRiskAssessment.challenge(60, List.of("Rapid outgoing transfers detected.")));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction response = transactionService.transferMoney(request, senderUser.getEmail());

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(senderWallet.getBalance()).isEqualByComparingTo("1150");
    }

    @Test
    void transferBlockedByScamShieldFailsEvenWhenAcknowledged() {
        TransferRequestDto request = new TransferRequestDto();
        request.setReceiverUpiId("receiver@upilite");
        request.setAmount(BigDecimal.valueOf(1200));
        request.setRiskAcknowledged(true);
        request.setPin("1234");

        mockSender();
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        when(walletRepository.findByUpiId("receiver@upilite")).thenReturn(Optional.of(receiverWallet));
        when(scamRiskService.evaluateTransferRisk(any(), any(), any(), any(), any()))
                .thenReturn(ScamRiskAssessment.block(85, List.of("High-value first-time transfer pattern.")));

        assertThatThrownBy(() -> transactionService.transferMoney(request, senderUser.getEmail()))
                .isInstanceOf(ScamRiskException.class)
                .hasMessageContaining("blocked by Scam Shield");
    }

    @Test
    void pagedHistoryWithCreditFilterUsesCreditQuery() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .receiverId(senderWallet.getId())
                .amount(BigDecimal.valueOf(99))
                .status(PaymentStatus.SUCCESS)
                .build();
        Page<Transaction> pagedResponse = new PageImpl<>(List.of(tx));

        when(walletRepository.findById(senderWallet.getId())).thenReturn(Optional.of(senderWallet));
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagedResponse);

        Page<Transaction> response = transactionService.getTransactionHistoryPage(
                senderWallet.getId(),
                "CREDIT",
                null,
                null,
                0,
                10);

        assertThat(response.getContent()).hasSize(1);
        verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void pagedHistoryWithInvalidTypeFails() {
        when(walletRepository.findById(senderWallet.getId())).thenReturn(Optional.of(senderWallet));

        assertThatThrownBy(() -> transactionService.getTransactionHistoryPage(
                senderWallet.getId(),
                "UNKNOWN",
                LocalDate.now(),
                LocalDate.now(),
                0,
                10))
                .isInstanceOf(InvalidTransferRequestException.class);
    }

    private void mockSender() {
        when(userRepository.findByEmail(senderUser.getEmail())).thenReturn(Optional.of(senderUser));
        when(userService.resolveWalletForUser(senderUser)).thenReturn(senderWallet);
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
            throw new IllegalStateException(exception);
        }
    }
}
