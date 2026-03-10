package com.stock.stockserver.domain.repository;

import com.stock.stockserver.domain.entity.FailedAnalysisRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FailedAnalysisRequestRepository extends JpaRepository<FailedAnalysisRequest, Long> {
    
    List<FailedAnalysisRequest> findByProcessedFalseOrderByFailedAtAsc();
    
    List<FailedAnalysisRequest> findByAnalysisId(String analysisId);
}
