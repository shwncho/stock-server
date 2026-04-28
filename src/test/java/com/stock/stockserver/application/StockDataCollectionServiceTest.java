package com.stock.stockserver.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.domain.AnalysisTarget;
import com.stock.stockserver.domain.repository.DailyPriceRepository;
import com.stock.stockserver.domain.repository.StockDataRepository;
import com.stock.stockserver.dto.DailyPriceDto;
import com.stock.stockserver.dto.StockDataDto;
import com.stock.stockserver.dto.VolumeRankDto;
import com.stock.stockserver.infrastructure.external.KisApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class StockDataCollectionServiceTest {

    private KisApiClient kisApiClient;
    private StockDataRepository stockDataRepository;
    private DailyPriceRepository dailyPriceRepository;
    private ObjectMapper objectMapper;
    private StockDataCollectionService service;

    @BeforeEach
    void setUp() {
        kisApiClient = mock(KisApiClient.class);
        stockDataRepository = mock(StockDataRepository.class);
        dailyPriceRepository = mock(DailyPriceRepository.class);
        objectMapper = mock(ObjectMapper.class);
        service = new StockDataCollectionService(
                kisApiClient,
                stockDataRepository,
                dailyPriceRepository,
                objectMapper,
                Runnable::run
        );
        ReflectionTestUtils.setField(service, "daysBack", 60);
    }

    @ParameterizedTest
    @MethodSource("singleTargets")
    @DisplayName("collectStockData - 국내/해외 단일 대상 수집")
    void collectStockData_singleTarget(AnalysisTarget target) throws Exception {
        VolumeRankDto rank = volumeRank(target);
        when(kisApiClient.getVolumeRankStocks(target)).thenReturn(List.of(rank));
        when(kisApiClient.getDailyData(target, rank.exchangeCode(), rank.stockCode(), 60))
                .thenReturn(List.of(dailyPrice(rank.stockCode())));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        List<StockDataDto> results = service.collectStockData(target);

        assertEquals(1, results.size());
        assertEquals(target, results.get(0).target());
        assertEquals(rank.exchangeCode(), results.get(0).exchangeCode());
        verify(kisApiClient).getVolumeRankStocks(target);
        verify(kisApiClient).getDailyData(target, rank.exchangeCode(), rank.stockCode(), 60);
        verify(stockDataRepository).save(any());
        verify(dailyPriceRepository).saveAll(any());
    }

    @Test
    @DisplayName("collectStockData - ALL 대상은 국내와 해외를 모두 수집")
    void collectStockData_allTarget() throws Exception {
        VolumeRankDto domesticRank = volumeRank(AnalysisTarget.DOMESTIC);
        VolumeRankDto overseasRank = volumeRank(AnalysisTarget.OVERSEAS);
        when(kisApiClient.getVolumeRankStocks(AnalysisTarget.DOMESTIC)).thenReturn(List.of(domesticRank));
        when(kisApiClient.getVolumeRankStocks(AnalysisTarget.OVERSEAS)).thenReturn(List.of(overseasRank));
        when(kisApiClient.getDailyData(any(), anyString(), anyString(), eq(60)))
                .thenAnswer(invocation -> List.of(dailyPrice(invocation.getArgument(2))));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        List<StockDataDto> results = service.collectStockData(AnalysisTarget.ALL);

        assertEquals(2, results.size());
        assertEquals(List.of(AnalysisTarget.DOMESTIC, AnalysisTarget.OVERSEAS),
                results.stream().map(StockDataDto::target).toList());
        verify(kisApiClient).getVolumeRankStocks(AnalysisTarget.DOMESTIC);
        verify(kisApiClient).getVolumeRankStocks(AnalysisTarget.OVERSEAS);
        verify(kisApiClient).getDailyData(
                AnalysisTarget.DOMESTIC, domesticRank.exchangeCode(), domesticRank.stockCode(), 60);
        verify(kisApiClient).getDailyData(
                AnalysisTarget.OVERSEAS, overseasRank.exchangeCode(), overseasRank.stockCode(), 60);
    }

    private VolumeRankDto volumeRank(AnalysisTarget target) {
        return VolumeRankDto.builder()
                .target(target)
                .exchangeCode(target == AnalysisTarget.OVERSEAS ? "NAS" : "KRX")
                .stockCode(target == AnalysisTarget.OVERSEAS ? "AAPL" : "005930")
                .stockName(target == AnalysisTarget.OVERSEAS ? "Apple" : "Samsung")
                .currentPrice(100.0)
                .changePercent(1.0)
                .tradingVolume(1000L)
                .tradingAmount(100000L)
                .rank(1)
                .build();
    }

    private DailyPriceDto dailyPrice(String stockCode) {
        return DailyPriceDto.builder()
                .stockCode(stockCode)
                .tradeDate(LocalDate.now())
                .openPrice(90.0)
                .closePrice(100.0)
                .highPrice(110.0)
                .lowPrice(80.0)
                .volume(1000L)
                .build();
    }

    private static Stream<AnalysisTarget> singleTargets() {
        return Stream.of(AnalysisTarget.DOMESTIC, AnalysisTarget.OVERSEAS);
    }
}
