package com.stock.stockserver.application;

public interface AnalysisRequestPublisher {

    void publish(String analysisId);

    void publishAndWait(String analysisId) throws Exception;
}
