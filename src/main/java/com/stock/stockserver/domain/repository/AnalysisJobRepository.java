package com.stock.stockserver.domain.repository;

import com.stock.stockserver.domain.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {

}
