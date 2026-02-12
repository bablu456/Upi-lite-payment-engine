package com.bablu.upilite.repository;

import com.bablu.upilite.entity.IdempotencyOperation;
import com.bablu.upilite.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByUserIdAndOperationAndIdempotencyKey(UUID userId,
                                                                           IdempotencyOperation operation,
                                                                           String idempotencyKey);
}
