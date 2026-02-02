package com.bablu.upilite.repository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository  extends JpaRepository<Wallet, UUID> {
}
