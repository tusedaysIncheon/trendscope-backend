package com.trendscope.backend.domain.analyze.scheduler;

import com.trendscope.backend.domain.analyze.entity.AnalyzeJobEntity;
import com.trendscope.backend.domain.analyze.repository.AnalyzeJobRepository;
import com.trendscope.backend.global.util.S3Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeRetentionScheduler {

    private final AnalyzeJobRepository analyzeJobRepository;
    private final S3Util s3Util;

    @Value("${app.analyze.photo-retention-days:1}")
    private long photoRetentionDays;

    @Value("${app.analyze.model-retention-days:365}")
    private long modelRetentionDays;

    @Value("${app.analyze.retention-batch-size:200}")
    private int retentionBatchSize;

    @Scheduled(cron = "${app.analyze.retention-cron:0 15 4 * * *}")
    @Transactional
    public void purgeExpiredAnalyzeData() {
        int batchSize = Math.max(1, Math.min(retentionBatchSize, 500));
        long photoDays = Math.max(1, photoRetentionDays);
        long modelDays = Math.max(photoDays, modelRetentionDays);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime photoCutoff = now.minusDays(photoDays);
        LocalDateTime modelCutoff = now.minusDays(modelDays);

        long scrubbedPhotoRows = purgeExpiredInputPhotos(photoCutoff, modelCutoff, batchSize);
        long deletedRows = purgeExpiredAnalyzeJobs(modelCutoff, batchSize);

        if (scrubbedPhotoRows > 0 || deletedRows > 0) {
            log.info(
                    "Analyze retention purge complete. photoCutoff={} modelCutoff={} scrubbedPhotoRows={} deletedRows={}",
                    photoCutoff,
                    modelCutoff,
                    scrubbedPhotoRows,
                    deletedRows
            );
        }
    }

    private long purgeExpiredInputPhotos(LocalDateTime photoCutoff, LocalDateTime modelCutoff, int batchSize) {
        long scrubbedRows = 0L;

        while (true) {
            List<AnalyzeJobEntity> targets = analyzeJobRepository
                    .findPhotoPurgeTargets(photoCutoff, modelCutoff, PageRequest.of(0, batchSize))
                    .getContent();
            if (targets.isEmpty()) {
                break;
            }

            for (AnalyzeJobEntity job : targets) {
                safeDelete(job.getFrontImageKey());
                safeDelete(job.getSideImageKey());
                job.clearInputImageKeys();
            }

            analyzeJobRepository.saveAll(targets);
            analyzeJobRepository.flush();
            scrubbedRows += targets.size();

            if (targets.size() < batchSize) {
                break;
            }
        }

        return scrubbedRows;
    }

    private long purgeExpiredAnalyzeJobs(LocalDateTime modelCutoff, int batchSize) {
        long deletedRows = 0L;

        while (true) {
            List<AnalyzeJobEntity> targets = analyzeJobRepository
                    .findByCompletedAtBeforeOrderByCompletedAtAsc(modelCutoff, PageRequest.of(0, batchSize))
                    .getContent();
            if (targets.isEmpty()) {
                break;
            }

            for (AnalyzeJobEntity job : targets) {
                safeDelete(job.getFrontImageKey());
                safeDelete(job.getSideImageKey());
                safeDelete(job.getGlbObjectKey());
            }

            analyzeJobRepository.deleteAllInBatch(targets);
            analyzeJobRepository.flush();
            deletedRows += targets.size();

            if (targets.size() < batchSize) {
                break;
            }
        }
        return deletedRows;
    }

    private void safeDelete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            s3Util.deleteObject(objectKey);
        } catch (Exception e) {
            log.warn("S3 object delete failed. key={}", objectKey, e);
        }
    }
}
