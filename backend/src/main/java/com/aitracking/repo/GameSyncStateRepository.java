package com.aitracking.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aitracking.domain.GameSyncState;

public interface GameSyncStateRepository extends JpaRepository<GameSyncState, String> {
}
