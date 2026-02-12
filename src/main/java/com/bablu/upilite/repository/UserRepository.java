package com.bablu.upilite.repository;

import com.bablu.upilite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository  extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
    Optional<User> findByMobile(String mobile);
    Optional<User> findByUpiId(String upiId);

    @Query("""
            SELECT u FROM User u
            WHERE (:query IS NULL OR :query = '' OR
                   LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
                   LOWER(u.upiId) LIKE LOWER(CONCAT('%', :query, '%')) OR
                   u.mobile LIKE CONCAT('%', :query, '%'))
            ORDER BY u.name ASC
            """)
    List<User> searchContacts(@Param("query") String query);

    // to check duplicate user
    boolean existsByEmail(String email);
    boolean existsByMobile(String mobile);
    boolean existsByName(String name);
    boolean existsByUpiId(String upiId);
}
