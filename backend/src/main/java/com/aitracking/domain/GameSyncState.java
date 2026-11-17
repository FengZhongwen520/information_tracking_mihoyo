package com.aitracking.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_sync_states")
@Getter
@Setter
@NoArgsConstructor
public class GameSyncState {

    @Id
    @Column(length = 20)
    private String game;

    @Column(length = 20)
    private String status = "never";

    private Instant lastAttemptAt;
    private Instant lastSuccessAt;

    @Column(length = 1000)
    private String lastError;

    private int activityCount;

    @Column(length = 40)
    private String version;
}
