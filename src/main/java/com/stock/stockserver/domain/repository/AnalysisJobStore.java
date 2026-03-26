package com.stock.stockserver.domain.repository;

import com.stock.stockserver.domain.AnalysisStatus;
import com.stock.stockserver.domain.entity.AnalysisJob;
import com.stock.stockserver.infrastructure.persistence.RedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Duration;

import static com.stock.stockserver.domain.AnalysisStatus.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AnalysisJobStore {

    private static final String KEY_PREFIX = "analysisJob::";
    private static final String ERROR_SUFFIX = ":error";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisRepository redisRepository;
    private final AnalysisJobRepository analysisJobRepository;

    public void save(AnalysisJob job) {
        String key = KEY_PREFIX + job.getAnalysisId();

        redisRepository.set(key, job.getStatus().name(), TTL);
        if (job.getErrorMessage() != null) {
            redisRepository.set(key + ERROR_SUFFIX, job.getErrorMessage(), TTL);
        }

        analysisJobRepository.save(job);

        log.info("AnalysisJob 저장 완료: analysisId={}, status={}", job.getAnalysisId(), job.getStatus());
    }

    public AnalysisJob get(String analysisId) {
        String key = KEY_PREFIX + analysisId;

        String statusStr = redisRepository.get(key);

        if (statusStr == null) {
            return analysisJobRepository.findById(analysisId).orElse(null);
        }

        AnalysisStatus status = valueOf(statusStr);
        String errorMessage = redisRepository.get(key + ERROR_SUFFIX);

        return AnalysisJob.builder()
                .analysisId(analysisId)
                .status(status)
                .errorMessage(errorMessage)
                .build();
    }

    public AnalysisStatus getStatus(String analysisId) {
        String key = KEY_PREFIX + analysisId;

        String statusStr = redisRepository.get(key);

        if (statusStr == null) {
            return analysisJobRepository.findById(analysisId)
                    .map(AnalysisJob::getStatus)
                    .orElse(null);
        }

        return valueOf(statusStr);
    }
}
