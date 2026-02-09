# Stock Analysis Server

한국투자증권(KIS) API와 LLM(GPT/Claude)을 연동하여 주식 종목을 분석하는 백엔드 서비스입니다.

## 🚀 주요 기능

- **거래량 Top 10 조회**: 한국투자증권 API를 통해 거래량 상위 종목을 조회합니다.
- **일봉 데이터 수집**: 각 종목의 60일 일봉 데이터를 수집합니다.
- **LLM 분석**: GPT-4o-mini 또는 Claude를 활용한 기술적 분석을 수행합니다.
- **투자 의견 생성**: 매수/매도/보유 의견을 자동 생성합니다.

## 🏗️ 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                   Stock Analysis Server                   │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  [KIS API] ───→ 데이터 수집 ───→ [LLM API]           │
│                    ↓                                      │
│                   MySQL                                   │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

## 🛠️ 기술 스택

- **Language**: Java 17
- **Framework**: Spring Boot 4.0.2
- **Build**: Gradle
- **Database**: MySQL 8.0
- **API Client**: WebClient (Reactive)
- **LLM**: OpenAI GPT-4o-mini, Claude-3.5

## 📁 프로젝트 구조

```
src/main/java/com/stock/stockserver/
├── application/          # 비즈니스 로직
│   ├── StockAnalysisService.java
│   └── StockDataCollectionService.java
├── domain/                # 도메인 모델
│   ├── entity/
│   │   ├── LLMAnalysisResult.java
│   │   ├── StockData.java
│   │   └── DailyPrice.java
│   └── repository/
├── dto/                   # 데이터 전송 객체
│   ├── StockDataDto.java
│   ├── VolumeRankDto.java
│   └── DailyPriceDto.java
├── infrastructure/         # 인프라스트럭처
│   ├── config/
│   │   ├── AsyncConfig.java
│   │   ├── WebClientConfig.java
│   │   └── CorsConfig.java
│   └── external/
│       ├── KisApiClient.java
│       └── LLMApiClient.java
└── presentation/          # API 엔드포인트
    ├── StockController.java
    └── AnalysisController.java
```

## 📡 API Endpoints

### 분석 관련

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/analysis/run` | 전체 분석 실행 |
| GET | `/api/analysis/status/{id}` | 분석 상태 조회 |
| GET | `/api/analysis/latest` | 최근 분석 결과 조회 |

### 주식 데이터

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/stocks/volume-rank` | 거래량 Top 10 조회 |
| GET | `/api/stocks/daily/{code}` | 특정 종목 일봉 조회 |

## ⚙️ 설정

### 환경 변수

```bash
# KIS API
KIS_APP_KEY=your_app_key
KIS_APP_SECRET=your_app_secret

# LLM API
OPENAI_API_KEY=your_openai_api_key
CLAUDE_API_KEY=your_claude_api_key

# Database
DATABASE_URL=jdbc:mysql://localhost:3306/stock_analysis
```

### application.yml 주요 설정

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30

kis:
  api:
    base-url: https://openapi.koreainvestment.com:9443
    connect-timeout: 5000
    read-timeout: 10000

llm:
  provider: gpt  # 또는 claude
  gpt:
    model: gpt-4o-mini
    max-tokens: 2000
```

## 🏃 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun

# 테스트 실행
./gradlew test
```

## 📈 성능 개선 스토리

### 문제 상황

초기 구현에서 `/api/analysis/run` API의 응답 시간이 **2~3분**이 걸렸습니다.

```
[문제]
- 순차적 API 호출 (KIS 10개 + LLM 10개)
- Blocking WebClient 사용
- 개별 트랜잭션 처리
```

### 원인 분석

| 단계 | 처리 방식 | 소요 시간 |
|------|----------|----------|
| KIS API 호출 | 순차 처리 | 20-50초 |
| LLM API 호출 | 순차 처리 | 100-150초 |
| DB 저장 | 개별 저장 | 5-10초 |
| **총계** | | **128-217초** |

### 해결 방안

#### 1. 비동기 병렬 처리

```java
// CompletableFuture를 활용한 병렬 처리
List<CompletableFuture<LLMAnalysisResult>> futures = stockDataList.stream()
    .map(stockData -> CompletableFuture.supplyAsync(() -> 
        llmApiClient.analyzeStock(stockData), 
        llmApiExecutor  // 커스텀 Executor 지정
    ))
    .collect(Collectors.toList());
```

#### 2. 커스텀 스레드 풀

```java
@Bean(name = "kisApiExecutor")
public Executor kisApiExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("KIS-API-");
    return executor;
}

@Bean(name = "llmApiExecutor")
public Executor llmApiExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("LLM-API-");
    return executor;
}
```

#### 3. WebClient 최적화

```java
@Bean
public WebClient webClient() {
    ConnectionProvider provider = ConnectionProvider.builder("stock-api")
        .maxConnections(50)
        .maxIdleTime(Duration.ofSeconds(30))
        .build();

    HttpClient httpClient = HttpClient.create(provider)
        .responseTimeout(Duration.ofSeconds(60));

    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
}
```

### 개선 결과

| 구분 | 개선 전 | 개선 후 | 개선율 |
|------|--------|--------|--------|
| KIS API 호출 | 20-50초 | 3-5초 | **90%** |
| LLM API 호출 | 100-150초 | 15-20초 | **87%** |
| **총 소요 시간** | **128-217초** | **18-25초** | **85%** |

```
[시각화]

Before: ████████████████████████████████████████ 128-217초
After:  ████ 18-25초

85% 성능 향상!
```

### 핵심 교훈

1. **I/O-Bound 작업은 비동기가 필수**: 네트워크 대기 시간 활용
2. **스레드 풀 크기 중요**: I/O 작업은 CPU 코어 수보다 많게 설정
3. **배치 처리 활용**: DB INSERT/UPDATE는 배치로 처리
4. **타임아웃 설정**: 외부 API 호출은 반드시 타임아웃 설정

## 📊 모니터링

로그 레벨 설정:

```yaml
logging:
  level:
    root: INFO
    com.stock: DEBUG
```

## 🔒 보안 주의사항

- API 키는 환경 변수로 관리
- CORS 설정 시 신뢰할 수 있는 도메인만 허용
- 프로덕션에서는 `ddl-auto: create` 변경
