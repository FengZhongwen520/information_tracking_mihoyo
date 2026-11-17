package com.aitracking.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aitracking.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    Optional<UserAccount> findByUsernameKey(String usernameKey);

    boolean existsByUsernameKey(String usernameKey);
}
