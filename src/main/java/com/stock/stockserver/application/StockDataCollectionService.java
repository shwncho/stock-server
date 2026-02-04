package com.stock.stockserver.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.domain.entity.DailyPrice;
import com.stock.stockserver.domain.entity.StockData;
import com.stock.stockserver.domain.repository.DailyPriceRepository;
import com.stock.stockserver.domain.repository.StockDataRepository;
import com.stock.stockserver.dto.DailyPriceDto;
import com.stock.stockserver.dto.StockDataDto;
import com.stock.stockserver.dto.VolumeRankDto;
import com.stock.stockserver.infrastructure.external.KisApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockDataCollectionService {

    private final KisApiClient kisApiClient;
    private final StockDataRepository stockDataRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final ObjectMapper objectMapper;

    @Value("${analysis.days-back}")
    private int daysBack;

    /**
     * 거래량 Top 10 종목의 데이터 수집 (병렬 처리)
     */
    @Transactional
    public List<StockDataDto> collectStockData() {
        log.info("=== 주식 데이터 수집 시작 (병렬 처리) ===");

        // 1단계: 거래량 Top 10 조회
        List<VolumeRankDto> topStocks = kisApiClient.getVolumeRankStocks();
        log.info("Step 1: 거래량 Top 10 조회 완료 - {} 개", topStocks.size());

        // 2단계: 각 종목별 데이터 수집 (병렬 처리)
        List<CompletableFuture<StockDataDto>> futures = topStocks.stream()
                .map(volumeRank -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return collectSingleStockData(volumeRank);
                    } catch (Exception e) {
                        log.error("데이터 수집 실패: {}", volumeRank.stockCode(), e);
                        return null;
                    }
                }))
                .collect(Collectors.toList());

        // 모든 병렬 작업 완료 대기 및 결과 수집
        List<StockDataDto> stockDataList = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("Step 2: 데이터 수집 완료 - {} 개", stockDataList.size());
        log.info("=== 주식 데이터 수집 완료 ===\n");

        return stockDataList;
    }

    /**
     * 개별 종목 데이터 수집
     */
    private StockDataDto collectSingleStockData(VolumeRankDto volumeRank) {
        String stockCode = volumeRank.stockCode();
        String stockName = volumeRank.stockName();

        log.debug("데이터 수집: {} ({})", stockCode, stockName);

        // 1. 일봉 데이터 조회 및 저장
        List<DailyPriceDto> dailyPriceDtos = kisApiClient.getDailyData(stockCode, daysBack);

        if (dailyPriceDtos == null || dailyPriceDtos.isEmpty()) {
            log.warn("일봉 데이터 없음: {}", stockCode);
            return null;
        }

        // DB에 저장
        saveDailyPrices(dailyPriceDtos);

        // 2. 52주 최고가/최저가 계산
        BigDecimal priceHigh52Week = BigDecimal.valueOf(
                dailyPriceDtos.stream()
                        .mapToDouble(DailyPriceDto::highPrice)
                        .max()
                        .orElse(0)
        );

        BigDecimal priceLow52Week = BigDecimal.valueOf(
                dailyPriceDtos.stream()
                        .mapToDouble(DailyPriceDto::lowPrice)
                        .min()
                        .orElse(0)
        );

        // 3. StockDataDto 생성
        StockDataDto stockData = StockDataDto.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .currentPrice(BigDecimal.valueOf(volumeRank.currentPrice()))
                .changePercent(BigDecimal.valueOf(volumeRank.changePercent()))
                .tradingVolume(volumeRank.tradingVolume())
                .tradingAmount(volumeRank.tradingAmount())
                .priceHigh52Week(priceHigh52Week)
                .priceLow52Week(priceLow52Week)
                .analysisDate(LocalDate.now())
                .dailyPricesJson(convertDailyPricesToJson(dailyPriceDtos))
                .build();

        // 4. DB 저장
        StockData entity = StockData.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .currentPrice(stockData.currentPrice())
                .changePercent(stockData.changePercent())
                .tradingVolume(stockData.tradingVolume())
                .tradingAmount(stockData.tradingAmount())
                .priceHigh52Week(priceHigh52Week)
                .priceLow52Week(priceLow52Week)
                .analysisDate(LocalDate.now())
                .dailyPricesJson(stockData.dailyPricesJson())
                .build();

        stockDataRepository.save(entity);

        log.debug("데이터 수집 완료: {}", stockCode);
        return stockData;
    }

    /**
     * 일봉 데이터 저장
     */
    private void saveDailyPrices(List<DailyPriceDto> dailyPriceDtos) {
        List<DailyPrice> dailyPrices = dailyPriceDtos.stream()
                .map(dto -> DailyPrice.builder()
                        .stockCode(dto.stockCode())
                        .tradeDate(dto.tradeDate())
                        .openPrice(BigDecimal.valueOf(dto.openPrice()))
                        .closePrice(BigDecimal.valueOf(dto.closePrice()))
                        .highPrice(BigDecimal.valueOf(dto.highPrice()))
                        .lowPrice(BigDecimal.valueOf(dto.lowPrice()))
                        .volume(dto.volume())
                        .build())
                .collect(Collectors.toList());

        dailyPriceRepository.saveAll(dailyPrices);
    }

    /**
     * 일봉 데이터를 JSON으로 변환
     */
    private String convertDailyPricesToJson(List<DailyPriceDto> dailyPrices) {
        try {
            return objectMapper.writeValueAsString(dailyPrices);
        } catch (Exception e) {
            log.error("JSON 변환 실패", e);
            return "[]";
        }
    }
}
