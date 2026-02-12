package com.bablu.upilite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_user_operation_key", columnNames = {"user_id", "operation", "idempotency_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private IdempotencyOperation operation;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 128, updatable = false)
    private String requestHash;

    @Column(name = "transaction_id", updatable = false)
    private UUID transactionId;

    @Column(name = "wallet_id", updatable = false)
    private UUID walletId;

    @Column(name = "balance_snapshot", updatable = false)
    private BigDecimal balanceSnapshot;

    @Column(name = "response_message", length = 255, updatable = false)
    private String responseMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
