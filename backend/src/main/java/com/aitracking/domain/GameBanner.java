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
@Table(name = "game_banners", indexes = {
        @Index(name = "idx_banners_game_end", columnList = "game,end_at")
})
@Getter
@Setter
@NoArgsConstructor
public class GameBanner {

    @Id
    @Column(length = 48)
    private String id;

    @Column(nullable = false, length = 20)
    private String game;

    @Column(length = 40)
    private String externalId;

    @Column(length = 200)
    private String name;

    @Column(length = 40)
    private String version;

    @Column(length = 80)
    private String bannerType;

    private OffsetDateTime startAt;
    private OffsetDateTime endAt;

    @Lob
    private String itemsJson;

    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant syncedAt;
}
