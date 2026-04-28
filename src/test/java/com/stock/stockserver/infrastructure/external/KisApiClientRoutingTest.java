package com.stock.stockserver.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.domain.AnalysisTarget;
import com.stock.stockserver.dto.DailyPriceDto;
import com.stock.stockserver.dto.VolumeRankDto;
import com.stock.stockserver.infrastructure.persistence.RedisRepository;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class KisApiClientRoutingTest {

    private KisApiClient kisApiClient;

    @BeforeEach
    void setUp() {
        kisApiClient = spy(new KisApiClient(
                mock(WebClient.class),
                mock(ObjectMapper.class),
                mock(RedisRepository.class),
                mock(RateLimiter.class),
                mock(Retry.class)
        ));
    }

    @Test
    @DisplayName("getVolumeRankStocks - 국내 대상은 국내 거래량 순위 API로 분기")
    void getVolumeRankStocks_domestic() {
        List<VolumeRankDto> expected = List.of(VolumeRankDto.builder()
                .target(AnalysisTarget.DOMESTIC)
                .stockCode("005930")
                .build());
        doReturn(expected).when(kisApiClient).getDomesticVolumeRankStocks();

        List<VolumeRankDto> result = kisApiClient.getVolumeRankStocks(AnalysisTarget.DOMESTIC);

        assertEquals(expected, result);
        verify(kisApiClient).getDomesticVolumeRankStocks();
        verify(kisApiClient, never()).getOverseasVolumeRankStocks();
    }

    @Test
    @DisplayName("getVolumeRankStocks - 해외 대상은 해외 거래량 순위 API로 분기")
    void getVolumeRankStocks_overseas() {
        List<VolumeRankDto> expected = List.of(VolumeRankDto.builder()
                .target(AnalysisTarget.OVERSEAS)
                .stockCode("AAPL")
                .build());
        doReturn(expected).when(kisApiClient).getOverseasVolumeRankStocks();

        List<VolumeRankDto> result = kisApiClient.getVolumeRankStocks(AnalysisTarget.OVERSEAS);

        assertEquals(expected, result);
        verify(kisApiClient).getOverseasVolumeRankStocks();
        verify(kisApiClient, never()).getDomesticVolumeRankStocks();
    }

    @Test
    @DisplayName("getDailyData - 국내 대상은 국내 기간별 시세 API로 분기")
    void getDailyData_domestic() {
        List<DailyPriceDto> expected = List.of(DailyPriceDto.builder().stockCode("005930").build());
        doReturn(expected).when(kisApiClient).getDomesticDailyData("005930", 60);

        List<DailyPriceDto> result = kisApiClient.getDailyData(
                AnalysisTarget.DOMESTIC, "KRX", "005930", 60);

        assertEquals(expected, result);
        verify(kisApiClient).getDomesticDailyData("005930", 60);
        verify(kisApiClient, never()).getOverseasDailyData(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("getDailyData - 해외 대상은 해외 기간별 시세 API로 분기")
    void getDailyData_overseas() {
        List<DailyPriceDto> expected = List.of(DailyPriceDto.builder().stockCode("AAPL").build());
        doReturn(expected).when(kisApiClient).getOverseasDailyData("NAS", "AAPL", 60);

        List<DailyPriceDto> result = kisApiClient.getDailyData(
                AnalysisTarget.OVERSEAS, "NAS", "AAPL", 60);

        assertEquals(expected, result);
        verify(kisApiClient).getOverseasDailyData("NAS", "AAPL", 60);
        verify(kisApiClient, never()).getDomesticDailyData(anyString(), anyInt());
    }
}
