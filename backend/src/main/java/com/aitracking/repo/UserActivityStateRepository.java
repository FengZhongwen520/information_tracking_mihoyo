package com.aitracking.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.aitracking.domain.UserActivityState;

public interface UserActivityStateRepository extends JpaRepository<UserActivityState, String> {

    List<UserActivityState> findByUserId(String userId);

    Optional<UserActivityState> findByUserIdAndActivityId(String userId, String activityId);

    @Modifying
    @Transactional
    @Query("delete from UserActivityState s where s.activityId in :activityIds")
    int deleteByActivityIdIn(@Param("activityIds") Collection<String> activityIds);

    @Modifying
    @Transactional
    @Query("delete from UserActivityState s where s.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);
}
