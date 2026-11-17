package com.aitracking.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aitracking.domain.GameCode;

public interface GameCodeRepository extends JpaRepository<GameCode, String> {

    List<GameCode> findByGameOrderByCodeAsc(String game);

    List<GameCode> findByGameAndActiveTrueOrderByCodeAsc(String game);

    long countByGameAndActiveTrue(String game);
}
