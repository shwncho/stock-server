package com.example.stockserver.presentation;

import com.example.stockserver.dto.DailyPriceDto;
import com.example.stockserver.dto.VolumeRankDto;
import com.example.stockserver.infrastructure.external.KisApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StockController {

    private final KisApiClient kisApiClient;

    @GetMapping("/volume-rank")
    public List<VolumeRankDto> getVolumeRank() {
        return kisApiClient.getVolumeRankStocks();
    }

    @GetMapping("/daily-price")
    public List<DailyPriceDto> getPriceChart() {
        return kisApiClient.getDailyData("000660",10);
    }
}
