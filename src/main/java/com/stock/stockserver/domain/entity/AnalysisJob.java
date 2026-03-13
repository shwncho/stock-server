package com.stock.stockserver.domain.entity;

import com.stock.stockserver.domain.AnalysisStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class AnalysisJob {

    @Id
    @Column(length = 36)
    private String analysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

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
