package com.aitracking.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_username_key", columnList = "username_key", unique = true))
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(name = "username_key", nullable = false, length = 40, unique = true)
    private String usernameKey;

    @Column(nullable = false, length = 220)
    private String passwordHash;

    private Instant createdAt;
    private Instant lastLoginAt;

    @Column(nullable = false)
    private int tokenVersion;

    @Column(length = 220)
    private String recoveryCodeHash;

    private Instant recoveryCodeCreatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_games", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "game", length = 20, nullable = false)
    private Set<String> games = new LinkedHashSet<>();
}
