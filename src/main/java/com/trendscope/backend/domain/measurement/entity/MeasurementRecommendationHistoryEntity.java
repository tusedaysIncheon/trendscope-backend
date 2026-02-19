package com.trendscope.backend.domain.measurement.entity;

import com.trendscope.backend.domain.analyze.entity.AnalyzeJobEntity;
import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;
import com.trendscope.backend.domain.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "measurement_recommendation_history",
        indexes = {
                @Index(name = "idx_measurement_recommendation_history_user_created", columnList = "user_id,created_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_measurement_recommendation_history_user_seq", columnNames = {"user_id", "user_seq"}),
                @UniqueConstraint(name = "uk_measurement_recommendation_history_analyze_job", columnNames = {"analyze_job_id"})
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementRecommendationHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "user_seq", nullable = false)
    private Long userSeq;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analyze_job_id", nullable = false, unique = true)
    private AnalyzeJobEntity analyzeJob;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private AnalyzeMode mode;

    @Column(name = "measurement_model", nullable = false, length = 20)
    private String measurementModel;

    @Column(name = "front_image_key", nullable = false, length = 512)
    private String frontImageKey;

    @Column(name = "side_image_key", length = 512)
    private String sideImageKey;

    @Column(name = "glb_object_key", nullable = false, length = 512)
    private String glbObjectKey;

    @Column(name = "result_json", nullable = false, columnDefinition = "text")
    private String resultJson;

    @Column(name = "llm_response_json", nullable = false, columnDefinition = "text")
    private String llmResponseJson;

    @Column(name = "llm_model", length = 100)
    private String llmModel;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
}
