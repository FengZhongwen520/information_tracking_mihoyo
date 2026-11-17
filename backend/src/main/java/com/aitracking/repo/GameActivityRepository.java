package com.aitracking.repo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aitracking.domain.GameActivity;

public interface GameActivityRepository extends JpaRepository<GameActivity, String> {

    List<GameActivity> findByGameInOrderByEndAtAsc(Collection<String> games);

    List<GameActivity> findByGameOrderByEndAtAsc(String game);

    long countByGame(String game);
}
