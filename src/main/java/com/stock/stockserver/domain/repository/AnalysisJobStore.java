package com.stock.stockserver.domain.repository;

import com.stock.stockserver.domain.AnalysisStatus;
import com.stock.stockserver.domain.entity.AnalysisJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import static com.stock.stockserver.domain.AnalysisStatus.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AnalysisJobStore {

    private static final String KEY_PREFIX = "analysisJob::";
    private static final String ERROR_SUFFIX = ":error";
    private static final long TTL_SECONDS = 86400;

    private final StringRedisTemplate redisTemplate;
    private final AnalysisJobRepository analysisJobRepository;

    public void save(AnalysisJob job) {
        String key = KEY_PREFIX + job.getAnalysisId();
        
        redisTemplate.opsForValue().set(key, job.getStatus().name());
        if (job.getErrorMessage() != null) {
            redisTemplate.opsForValue().set(key + ERROR_SUFFIX, job.getErrorMessage());
        }
        redisTemplate.expire(key, java.time.Duration.ofSeconds(TTL_SECONDS));
        
        analysisJobRepository.save(job);
        
        log.info("AnalysisJob 저장 완료: analysisId={}, status={}", job.getAnalysisId(), job.getStatus());
    }

    public AnalysisJob get(String analysisId) {
        String key = KEY_PREFIX + analysisId;
        
        String statusStr = redisTemplate.opsForValue().get(key);
        
        if (statusStr == null) {
            return analysisJobRepository.findById(analysisId).orElse(null);
        }
        
        AnalysisStatus status = valueOf(statusStr);
        String errorMessage = redisTemplate.opsForValue().get(key + ERROR_SUFFIX);
        
        return AnalysisJob.builder()
                .analysisId(analysisId)
                .status(status)
                .errorMessage(errorMessage)
                .build();
    }

    public AnalysisStatus getStatus(String analysisId) {
        String key = KEY_PREFIX + analysisId;
        
        String statusStr = redisTemplate.opsForValue().get(key);
        
        if (statusStr == null) {
            return analysisJobRepository.findById(analysisId)
                    .map(AnalysisJob::getStatus)
                    .orElse(null);
        }
        
        return valueOf(statusStr);
    }
}
