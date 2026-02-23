# Stock Analysis Server

한국투자증권(KIS) API와 LLM(GPT/Claude)을 연동하여 주식 종목을 분석하는 백엔드 서비스입니다.

## 🚀 주요 기능

- **거래량 Top 10 조회**: 한국투자증권 API를 통해 거래량 상위 종목을 조회합니다.
- **일봉 데이터 수집**: 각 종목의 60일 일봉 데이터를 수집합니다.
- **LLM 분석**: GPT-4o-mini 또는 Claude를 활용한 기술적 분석을 수행합니다.
- **투자 의견 생성**: 매수/매도/보유 의견을 자동 생성합니다.

## 🏗️ 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Stock Analysis Server                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐              │
│  │   KIS API    │    │   LLM API    │    │   MySQL      │              │
│  └──────┬───────┘    └──────┬───────┘    └──────────────┘              │
│         │                   │                                        │
│         └─────────┬─────────┘                                        │
│                   ▼                                                   │
│         ┌────────────────┐                                          │
│         │   Redis Cache  │  ← KIS/API 응답, LLM 분석 결과 캐싱       │
│         └────────┬───────┘                                          │
│                  │                                                   │
│         ┌───────▼────────┐                                          │
│         │  Kafka Queue   │  ← 비동기 분석 처리                       │
│         └────────┬───────┘                                          │
│                  │                                                   │
│         ┌───────▼────────┐                                          │
│         │  Consumer      │                                          │
│         └────────────────┘                                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## 🛠️ 기술 스택

- **Language**: Java 17
- **Framework**: Spring Boot 4.0.2
- **Build**: Gradle
- **Database**: MySQL 8.0
- **Cache**: Redis
- **Message Queue**: Apache Kafka
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
│   │   ├── CorsConfig.java
│   │   ├── RedisCacheConfig.java    # Redis 캐시 설정
│   │   └── KafkaConfig.java         # Kafka 설정
│   ├── consumer/
│   │   └── AnalysisConsumer.java    # Kafka Consumer
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
3.  **타임아웃 설정**: 외부 API 호출은 반드시 타임아웃 설정

---

## 🚀 추가 성능 개선: Redis 캐싱

### 문제 상황

- **KIS API 호출 제한**: 외부 API 호출 지연
- **LLM 비용**: 동일한 분석결과 중복 호출로 인한 비용 증가
- **응답 속도**: 빈번한 조회 요청 시 매번 API 호출로 인한 지연

### 해결 방안: Redis 기반 캐싱

```java
// KIS 거래량 조회 캐싱 (10분 TTL)
@Cacheable(cacheNames = "kisVolumeRankCache")
public List<VolumeRankDto> getVolumeRank() { ... }

// KIS 일봉 데이터 캐싱 (1시간 TTL)
@Cacheable(cacheNames = "kisDailyCache", key = "#stockCode + ':' + #days")
public List<DailyPriceDto> getDailyPrices(String stockCode, int days) { ... }

// LLM 분석 결과 캐싱 (6시간 TTL)
@Cacheable(cacheNames = "llmAnalysisCache", key = "#stockCode")
public LLMAnalysisResponseDto analyzeStock(StockDataDto stockData) { ... }
```

### 캐시 설정

| 캐시 이름 | 용도 | TTL |
|-----------|------|-----|
| `kisVolumeRankCache` | 거래량 Top 10 | 10분 |
| `kisDailyCache` | 일봉 데이터 | 1시간 |
| `llmAnalysisCache` | LLM 분석 결과 | 6시간 |

### 캐싱 효과

| 구분 | 캐시 미사용 | 캐시 사용 | 효과 |
|------|------------|----------|------|
| 거래량 조회 | 500-1000ms | **< 10ms** | **99%↓** |
| 일봉 조회 | 300-500ms | **< 10ms** | **97%↓** |
| LLM 분석 | 10-15초 (API 비용) | **< 10ms** | **비용 절감** |

---

## 🔄 Kafka 기반 비동기 처리

### 문제 상황

- 분석 API 요청 시 처리 완료까지 **18-25초** 동안 응답 대기
- 서버 재시작 시 진행 중인 분석 작업 손실
- 단일 서버 환경에서 처리량 한계

### 해결 방안: Kafka Message Queue

```
API 요청 → Kafka Producer → [analysis-requests 토픽] → Consumer → 분석 로직
    ↑                                                        │
    └──────────────────── 즉시 응답 ──────────────────────────┘
```

### 핵심 구현

```java
// Producer: 분석 요청을 Kafka로 전송
@PostMapping("/run")
public ResponseEntity<PostAnalysisDto> runAnalysis() {
    kafkaTemplate.send("analysis-requests", analysisId, message);
    return ResponseEntity.ok(new PostAnalysisDto(analysisId, RUNNING));
}

// Consumer: 토픽에서 메시지를 받아 비동기 처리
@KafkaListener(topics = "analysis-requests")
public void consumeAnalysisRequest(String message) {
    analysisService.runFullAnalysis(analysisId);
}
```

### Kafka 도입 효과

| 구분 | 도입 전 | 도입 후 | 효과 |
|------|--------|--------|------|
| **API 응답 시간** | 18-25초 | **< 100ms** | **즉시 응답** |
| **서버 재시작** | 작업 손실 | **메시지 보존** | **안정성** |
| **확장성** | 단일 서버 | **수평 확장** | **처리량 증가** |
| **장애 복구** | 어려움 | **DLQ 지원** | **신뢰성** |

### DLQ (Dead Letter Queue)

실패한 메시지를 별도의 토픽(`analysis-requests.DLT`)으로 전송하여 문제 분석 및 재처리가 가능

```
재시도 3회 실패 → DLQ 토픽으로 자동 이동 → 나중에 수동/자동 재처리
```

---

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
