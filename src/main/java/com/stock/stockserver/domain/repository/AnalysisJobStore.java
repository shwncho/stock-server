package com.stock.stockserver.domain.repository;

import com.stock.stockserver.domain.AnalysisJob;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AnalysisJobStore {
    private final Map<String, AnalysisJob> store = new ConcurrentHashMap<>();

    public void save(AnalysisJob job) {
        store.put(job.getAnalysisId(), job);
    }

    public AnalysisJob get(String id) {
        return store.get(id);
    }
}
