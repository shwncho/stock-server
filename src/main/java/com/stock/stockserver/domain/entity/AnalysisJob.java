package com.stock.stockserver.domain.entity;

import com.stock.stockserver.domain.AnalysisStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AnalysisJob {

    @Id
    @Column(length = 36)
    private String analysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Builder
    public AnalysisJob(String analysisId, AnalysisStatus status, String errorMessage) {
        this.analysisId = analysisId;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public void updateStatus(AnalysisStatus status) {
        this.status = status;
    }

    public void updateStatusWithError(AnalysisStatus status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }
}
