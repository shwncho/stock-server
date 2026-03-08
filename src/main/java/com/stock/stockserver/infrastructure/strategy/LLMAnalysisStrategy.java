package com.stock.stockserver.infrastructure.strategy;

import com.stock.stockserver.dto.StockDataDto;

public interface LLMAnalysisStrategy {
    
    String getProviderName();
    
    String analyze(StockDataDto stockData);
    
    String buildPrompt(StockDataDto stockData);
}
