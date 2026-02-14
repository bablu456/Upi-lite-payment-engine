package com.bablu.upilite.repository;

import com.bablu.upilite.entity.DisputeCase;
import com.bablu.upilite.entity.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeCaseRepository extends JpaRepository<DisputeCase, UUID> {

    List<DisputeCase> findByRaisedByUserIdOrderByCreatedAtDesc(UUID raisedByUserId);

    List<DisputeCase> findByRaisedByUserIdAndStatusOrderByCreatedAtDesc(UUID raisedByUserId, DisputeStatus status);

    Optional<DisputeCase> findByIdAndRaisedByUserId(UUID id, UUID raisedByUserId);

    boolean existsByTransactionIdAndRaisedByUserIdAndStatusNot(UUID transactionId,
                                                               UUID raisedByUserId,
                                                               DisputeStatus status);
}
