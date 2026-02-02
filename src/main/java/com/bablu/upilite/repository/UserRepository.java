package com.bablu.upilite.repository;

import com.bablu.upilite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository  extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    // to check duplicate user
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
