package com.aitracking.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_activity_states",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_activity", columnNames = {"user_id", "activity_id"}),
        indexes = @Index(name = "idx_states_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class UserActivityState {

    @Id
    @Column(length = 96)
    private String id;

    @Column(name = "user_id", nullable = false, length = 40)
    private String userId;

    @Column(name = "activity_id", nullable = false, length = 48)
    private String activityId;

    private boolean completed;
    private Instant completedAt;
    private Instant updatedAt;
}
