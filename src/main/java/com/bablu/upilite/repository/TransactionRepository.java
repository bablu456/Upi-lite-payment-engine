package com.bablu.upilite.repository;

import com.bablu.upilite.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findBySenderIdOrReceiverId(UUID senderId, UUID receivedId);

    List<Transaction> findBySenderIdOrReceiverIdOrderByTimestampDesc(UUID senderId, UUID receiverId);

    boolean existsBySenderIdAndReceiverId(UUID senderId, UUID receiverId);

    long countBySenderIdAndTimestampAfter(UUID senderId, LocalDateTime timestamp);
}
