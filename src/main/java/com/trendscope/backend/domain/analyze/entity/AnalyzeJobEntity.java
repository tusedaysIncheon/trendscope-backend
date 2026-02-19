package com.trendscope.backend.domain.analyze.entity;

import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeJobStatus;
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
        name = "analyze_job",
        indexes = {
                @Index(name = "idx_analyze_job_user_created", columnList = "user_id,created_date"),
                @Index(name = "idx_analyze_job_status_created", columnList = "status,created_date")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true, updatable = false, length = 64)
    private String jobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private AnalyzeMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalyzeJobStatus status;

    @Column(name = "front_image_key", nullable = false, length = 512)
    private String frontImageKey;

    @Column(name = "side_image_key", length = 512)
    private String sideImageKey;

    @Column(name = "glb_object_key", nullable = false, length = 512)
    private String glbObjectKey;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "quality_mode", length = 20)
    private String qualityMode;

    @Column(name = "normalize_with_anny", nullable = false)
    private Boolean normalizeWithAnny;

    @Column(name = "measurement_model", length = 20)
    private String measurementModel;

    @Column(name = "output_pose", length = 20)
    private String outputPose;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_detail", columnDefinition = "text")
    private String errorDetail;

    @Column(name = "queued_at")
    private LocalDateTime queuedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    public void setInputProfile(
            Double heightCm,
            Double weightKg,
            String gender,
            String qualityMode,
            Boolean normalizeWithAnny,
            String measurementModel,
            String outputPose
    ) {
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.gender = gender;
        this.qualityMode = qualityMode;
        this.normalizeWithAnny = normalizeWithAnny;
        this.measurementModel = measurementModel;
        this.outputPose = outputPose;
    }

    public void markQueued() {
        this.status = AnalyzeJobStatus.QUEUED;
        this.queuedAt = LocalDateTime.now();
        this.errorCode = null;
        this.errorDetail = null;
    }

    public void markRunning() {
        this.status = AnalyzeJobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.errorCode = null;
        this.errorDetail = null;
    }

    public void markCompleted(String resultJson) {
        this.status = AnalyzeJobStatus.COMPLETED;
        this.resultJson = resultJson;
        this.errorCode = null;
        this.errorDetail = null;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String errorCode, String errorDetail) {
        this.status = AnalyzeJobStatus.FAILED;
        this.resultJson = null;
        this.errorCode = errorCode;
        this.errorDetail = errorDetail;
        this.completedAt = LocalDateTime.now();
    }
}
