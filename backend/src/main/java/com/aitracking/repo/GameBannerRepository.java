package com.aitracking.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aitracking.domain.GameBanner;

public interface GameBannerRepository extends JpaRepository<GameBanner, String> {

    List<GameBanner> findByGameOrderByEndAtAsc(String game);

    long countByGame(String game);
}
