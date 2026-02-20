package com.trendscope.backend.domain.analyze.repository;

import com.trendscope.backend.domain.analyze.entity.AnalyzeJobEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AnalyzeJobRepository extends JpaRepository<AnalyzeJobEntity, Long> {
    Optional<AnalyzeJobEntity> findByJobIdAndUserUsername(String jobId, String username);

    Optional<AnalyzeJobEntity> findByJobId(String jobId);

    Page<AnalyzeJobEntity> findByUserUsernameOrderByCreatedDateDesc(String username, Pageable pageable);

    Page<AnalyzeJobEntity> findByCompletedAtBeforeOrderByCompletedAtAsc(LocalDateTime cutoff, Pageable pageable);

    @Query("""
            select j
            from AnalyzeJobEntity j
            where j.completedAt is not null
              and j.completedAt <= :photoCutoff
              and j.completedAt > :modelCutoff
              and (
                (j.frontImageKey is not null and j.frontImageKey <> '')
                or (j.sideImageKey is not null and j.sideImageKey <> '')
              )
            order by j.completedAt asc
            """)
    Page<AnalyzeJobEntity> findPhotoPurgeTargets(
            @Param("photoCutoff") LocalDateTime photoCutoff,
            @Param("modelCutoff") LocalDateTime modelCutoff,
            Pageable pageable
    );

    long deleteByCompletedAtBefore(LocalDateTime cutoff);
}
