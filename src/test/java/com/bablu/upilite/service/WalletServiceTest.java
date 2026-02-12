package com.bablu.upilite.service;

import com.bablu.upilite.dto.WalletBalanceResponseDto;
import com.bablu.upilite.dto.WalletCreditRequestDto;
import com.bablu.upilite.entity.IdempotencyOperation;
import com.bablu.upilite.entity.IdempotencyRecord;
import com.bablu.upilite.entity.LedgerEntry;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.entity.Wallet;
import com.bablu.upilite.exception.InvalidTransferRequestException;
import com.bablu.upilite.exception.WalletLimitExceededException;
import com.bablu.upilite.repository.IdempotencyRecordRepository;
import com.bablu.upilite.repository.LedgerEntryRepository;
import com.bablu.upilite.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @InjectMocks
    private WalletService walletService;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("wallet-owner@upi.test")
                .build();

        wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .balance(BigDecimal.valueOf(1200))
                .upiId("wallet-owner@upilite")
                .build();
    }

    @Test
    void creditWalletSucceedsWithinLimit() {
        WalletCreditRequestDto request = new WalletCreditRequestDto();
        request.setAmount(BigDecimal.valueOf(300));

        when(userService.getUserByEmailOrThrow(user.getEmail())).thenReturn(user);
        when(userService.resolveWalletForUser(user)).thenReturn(wallet);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WalletBalanceResponseDto response = walletService.creditWallet(user.getEmail(), request);

        assertThat(response.getBalance()).isEqualByComparingTo("1500");
        assertThat(response.getWalletId()).isEqualTo(wallet.getId());
        verify(walletRepository).save(wallet);
    }

    @Test
    void creditWalletFailsWhenBalanceWouldExceedLimit() {
        WalletCreditRequestDto request = new WalletCreditRequestDto();
        request.setAmount(BigDecimal.valueOf(1000));

        when(userService.getUserByEmailOrThrow(user.getEmail())).thenReturn(user);
        when(userService.resolveWalletForUser(user)).thenReturn(wallet);

        assertThatThrownBy(() -> walletService.creditWallet(user.getEmail(), request))
                .isInstanceOf(WalletLimitExceededException.class);
    }

    @Test
    void creditWalletFailsWhenAmountInvalid() {
        WalletCreditRequestDto request = new WalletCreditRequestDto();
        request.setAmount(BigDecimal.ZERO);

        assertThatThrownBy(() -> walletService.creditWallet(user.getEmail(), request))
                .isInstanceOf(InvalidTransferRequestException.class);
    }

    @Test
    void creditWalletWithSameIdempotencyKeyReturnsExistingResponse() {
        WalletCreditRequestDto request = new WalletCreditRequestDto();
        request.setAmount(BigDecimal.valueOf(150));

        String idempotencyKey = "credit-key-12345";
        IdempotencyRecord record = IdempotencyRecord.builder()
                .userId(user.getId())
                .operation(IdempotencyOperation.WALLET_CREDIT)
                .idempotencyKey(idempotencyKey)
                .requestHash(hashCreditRequest(request))
                .walletId(wallet.getId())
                .balanceSnapshot(BigDecimal.valueOf(1350))
                .responseMessage("Wallet credited successfully.")
                .build();

        when(userService.getUserByEmailOrThrow(user.getEmail())).thenReturn(user);
        when(idempotencyRecordRepository.findByUserIdAndOperationAndIdempotencyKey(
                user.getId(),
                IdempotencyOperation.WALLET_CREDIT,
                idempotencyKey)).thenReturn(Optional.of(record));

        WalletBalanceResponseDto response = walletService.creditWallet(user.getEmail(), request, idempotencyKey);

        assertThat(response.getWalletId()).isEqualTo(wallet.getId());
        assertThat(response.getBalance()).isEqualByComparingTo("1350");
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    private String hashCreditRequest(WalletCreditRequestDto request) {
        String amount = request.getAmount() == null
                ? ""
                : request.getAmount().stripTrailingZeros().toPlainString();
        return sha256Hex(amount);
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
