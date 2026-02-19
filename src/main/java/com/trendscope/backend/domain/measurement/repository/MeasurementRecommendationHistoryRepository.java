package com.trendscope.backend.domain.measurement.repository;

import com.trendscope.backend.domain.measurement.entity.MeasurementRecommendationHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeasurementRecommendationHistoryRepository extends JpaRepository<MeasurementRecommendationHistoryEntity, Long> {

    Optional<MeasurementRecommendationHistoryEntity> findByAnalyzeJob_Id(Long analyzeJobId);

    Optional<MeasurementRecommendationHistoryEntity> findByAnalyzeJob_JobIdAndUser_Username(String jobId, String username);

    Optional<MeasurementRecommendationHistoryEntity> findByUser_UsernameAndUserSeq(String username, Long userSeq);

    Optional<MeasurementRecommendationHistoryEntity> findTopByUser_IdOrderByUserSeqDesc(Long userId);

    Page<MeasurementRecommendationHistoryEntity> findByUser_UsernameOrderByCreatedDateDesc(String username, Pageable pageable);
}
