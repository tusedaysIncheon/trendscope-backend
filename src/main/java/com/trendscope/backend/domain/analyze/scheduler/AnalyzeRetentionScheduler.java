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

    @Value("${app.analyze.retention-days:30}")
    private long retentionDays;

    @Value("${app.analyze.retention-batch-size:200}")
    private int retentionBatchSize;

    @Scheduled(cron = "${app.analyze.retention-cron:0 15 4 * * *}")
    @Transactional
    public void purgeExpiredAnalyzeData() {
        int batchSize = Math.max(1, Math.min(retentionBatchSize, 500));
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        long deletedRows = 0L;

        while (true) {
            List<AnalyzeJobEntity> targets = analyzeJobRepository
                    .findByCompletedAtBeforeOrderByCompletedAtAsc(cutoff, PageRequest.of(0, batchSize))
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
            deletedRows += targets.size();

            if (targets.size() < batchSize) {
                break;
            }
        }

        if (deletedRows > 0) {
            log.info("Analyze retention purge complete. cutoff={} deletedRows={}", cutoff, deletedRows);
        }
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
