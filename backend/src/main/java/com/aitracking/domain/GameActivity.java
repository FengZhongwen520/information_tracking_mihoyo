package com.aitracking.domain;

import java.time.Instant;
import java.time.OffsetDateTime;

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
@Table(name = "activities", indexes = {
        @Index(name = "idx_activities_game_end", columnList = "game,end_at"),
        @Index(name = "idx_activities_game_name", columnList = "game,name")
})
@Getter
@Setter
@NoArgsConstructor
public class GameActivity {

    @Id
    @Column(length = 48)
    private String id;

    @Column(nullable = false, length = 20)
    private String game;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(length = 80)
    private String kind;

    @Lob
    private String description;

    private OffsetDateTime startAt;
    private OffsetDateTime endAt;

    @Column(length = 1000)
    private String cover;

    @Lob
    private String rewardsJson;

    @Column(length = 40)
    private String version;

    @Column(length = 200)
    private String versionName;

    @Column(length = 40)
    private String challengeStage;

    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant syncedAt;
}
