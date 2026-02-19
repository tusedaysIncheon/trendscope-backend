package com.trendscope.backend.domain.analyze.repository;

import com.trendscope.backend.domain.analyze.entity.AnalyzeJobEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AnalyzeJobRepository extends JpaRepository<AnalyzeJobEntity, Long> {
    Optional<AnalyzeJobEntity> findByJobIdAndUserUsername(String jobId, String username);

    Optional<AnalyzeJobEntity> findByJobId(String jobId);

    Page<AnalyzeJobEntity> findByUserUsernameOrderByCreatedDateDesc(String username, Pageable pageable);

    Page<AnalyzeJobEntity> findByCompletedAtBeforeOrderByCompletedAtAsc(LocalDateTime cutoff, Pageable pageable);

    long deleteByCompletedAtBefore(LocalDateTime cutoff);
}
