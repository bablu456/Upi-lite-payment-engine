package com.bablu.upilite.repository;

import com.bablu.upilite.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findBySenderIdOrReceiverId(UUID senderId, UUID receivedId);
}
