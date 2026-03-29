package com.stock.stockserver.application;

import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import com.stock.stockserver.domain.repository.LLMAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisResultSaveService {

    private final LLMAnalysisResultRepository analysisResultRepository;

    @Transactional
    public void saveAll(List<LLMAnalysisResult> results) {
        analysisResultRepository.saveAll(results);
        log.info("DB 배치 저장 완료: {} 개", results.size());
    }
}
