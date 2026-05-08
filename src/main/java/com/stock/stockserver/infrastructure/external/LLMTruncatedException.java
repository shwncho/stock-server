package com.stock.stockserver.infrastructure.external;

import lombok.Getter;

@Getter
public class LLMTruncatedException extends RuntimeException {

    private final String provider;
    private final String reason;

    public LLMTruncatedException(String provider, String reason) {
        super("LLM 응답이 max_tokens로 잘렸습니다: provider=" + provider + ", reason=" + reason);
        this.provider = provider;
        this.reason = reason;
    }
}
