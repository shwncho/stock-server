package com.stock.stockserver.domain.repository;

import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LLMAnalysisResultRepository extends JpaRepository<LLMAnalysisResult, Long> {
    Optional<LLMAnalysisResult> findByStockCodeAndAnalysisDate(String stockCode, LocalDate analysisDate);

    List<LLMAnalysisResult> findTop10ByAnalysisDateOrderByCreatedAtDesc(LocalDate analysisDate);
}

