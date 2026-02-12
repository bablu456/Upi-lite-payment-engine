package com.bablu.upilite.repository;

import com.bablu.upilite.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
