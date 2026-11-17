package com.aitracking.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_codes", indexes = {
        @Index(name = "idx_codes_game_active", columnList = "game,active")
})
@Getter
@Setter
@NoArgsConstructor
public class GameCode {

    @Id
    @Column(length = 48)
    private String id;

    @Column(nullable = false, length = 20)
    private String game;

    @Column(name = "code_value", nullable = false, length = 100)
    private String code;

    private boolean active;

    @Lob
    private String rewardsJson;

    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant syncedAt;
}
