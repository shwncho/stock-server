package com.stock.stockserver.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "failed_analysis_requests")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedAnalysisRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String analysisId;

    @Column(nullable = false)
    private String originalMessage;

    @Column
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime failedAt;

    @Column
    private LocalDateTime processedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    public void markAsProcessed() {
        this.processed = true;
        this.processedAt = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }
}
