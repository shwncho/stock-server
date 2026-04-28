package com.stock.stockserver.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.domain.AnalysisTarget;
import com.stock.stockserver.domain.entity.FailedAnalysisRequest;
import com.stock.stockserver.domain.repository.FailedAnalysisRequestRepository;
import com.stock.stockserver.dto.AnalysisEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DltRetryService {

    private final FailedAnalysisRequestRepository failedAnalysisRequestRepository;
    private final AnalysisRequestPublisher analysisRequestPublisher;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_COUNT = 3;

    @Scheduled(initialDelay = 60000, fixedDelay = 300000)
    @Transactional
    public void retryFailedRequests() {
        log.info("DLT 재시도 스케줄러 실행");
        
        List<FailedAnalysisRequest> failedRequests = 
                failedAnalysisRequestRepository.findByProcessedFalseOrderByFailedAtAsc();
        
        if (failedRequests.isEmpty()) {
            log.info("재시도할 실패 요청이 없습니다.");
            return;
        }
        
        log.info("재시도할 실패 요청 수: {}", failedRequests.size());
        
        for (FailedAnalysisRequest failedRequest : failedRequests) {
            try {
                retryRequest(failedRequest);
            } catch (Exception e) {
                log.error("재시도 실패: analysisId={}, error={}", 
                        failedRequest.getAnalysisId(), e.getMessage());
            }
        }
    }

    private void retryRequest(FailedAnalysisRequest failedRequest) throws Exception {
        String analysisId = failedRequest.getAnalysisId();
        
        if (failedRequest.getRetryCount() >= MAX_RETRY_COUNT) {
            log.warn("최대 재시도 횟수 초과: analysisId={}, retryCount={}", 
                    analysisId, failedRequest.getRetryCount());
            failedRequest.markAsProcessed();
            failedAnalysisRequestRepository.save(failedRequest);
            return;
        }
        
        AnalysisTarget target = resolveTarget(failedRequest);
        analysisRequestPublisher.publishAndWaitForAck(analysisId, target);

        log.info("DLT 메시지 재전송 성공: analysisId={}", analysisId);

        failedRequest.incrementRetryCount();
        failedRequest.markAsProcessed();
        failedAnalysisRequestRepository.save(failedRequest);
    }

    private AnalysisTarget resolveTarget(FailedAnalysisRequest failedRequest) {
        try {
            AnalysisEvent event = objectMapper.readValue(failedRequest.getOriginalMessage(), AnalysisEvent.class);
            return event.resolvedTarget();
        } catch (Exception e) {
            log.warn("실패 메시지에서 분석 대상을 읽지 못해 ALL로 재시도합니다. analysisId={}",
                    failedRequest.getAnalysisId(), e);
            return AnalysisTarget.ALL;
        }
    }
}
