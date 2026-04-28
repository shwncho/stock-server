package com.stock.stockserver.domain;

import java.util.List;

public enum AnalysisTarget {
    DOMESTIC,
    OVERSEAS,
    ALL;

    public List<AnalysisTarget> expand() {
        if (this == ALL) {
            return List.of(DOMESTIC, OVERSEAS);
        }
        return List.of(this);
    }
}
