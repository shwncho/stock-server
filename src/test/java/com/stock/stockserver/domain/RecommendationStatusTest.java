package com.stock.stockserver.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationStatusTest {

    @Test
    @DisplayName("RecommendationStatus enum 값 확인")
    void recommendationStatus_values() {
        assertEquals(3, RecommendationStatus.values().length);
        assertNotNull(RecommendationStatus.BUY);
        assertNotNull(RecommendationStatus.SELL);
        assertNotNull(RecommendationStatus.HOLD);
    }

    @Test
    @DisplayName("RecommendationStatus 이름 확인")
    void recommendationStatus_names() {
        assertEquals("BUY", RecommendationStatus.BUY.name());
        assertEquals("SELL", RecommendationStatus.SELL.name());
        assertEquals("HOLD", RecommendationStatus.HOLD.name());
    }
}
