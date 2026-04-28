package com.stock.stockserver.application;

import com.stock.stockserver.domain.AnalysisTarget;

public interface AnalysisRequestPublisher {

    void publish(String analysisId);

    void publish(String analysisId, AnalysisTarget target);

    void publishAndWaitForAck(String analysisId) throws Exception;

    void publishAndWaitForAck(String analysisId, AnalysisTarget target) throws Exception;
}
