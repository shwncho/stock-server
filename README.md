# Stock Analysis Server

한국투자증권(KIS) API와 LLM(GPT/Claude)을 연동하여 **국내·해외 주식 종목**을 분석하는 백엔드 서비스입니다.

## 🚀 주요 기능

- **국내/해외 거래량 Top 10 조회**: KIS API를 통해 국내(KRX) 및 해외(NAS/NYS/AMS) 거래량 상위 종목을 조회
- **일봉 데이터 수집**: 각 종목의 60일 일봉 데이터를 병렬 수집
- **LLM 분석**: GPT-4o-mini 또는 Claude를 활용한 기술적 분석
- **투자 의견 생성**: 매수/매도/보유 의견 자동 생성
- **LLM Fallback**: Primary LLM 실패 시 Secondary LLM로 자동 전환
- **KIS API 유량 제어**: Resilience4j RateLimiter + Retry로 KIS의 초당 호출 한도 보호
- **Graceful Shutdown**: 서버 종료 시 처리 중인 Kafka 메시지를 모두 소화한 뒤 종료

## 🏗️ 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Stock Analysis Server                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐              │
│  │   KIS API    │    │   LLM API    │    │   MySQL      │              │
│  │ 국내 + 해외  │    │ (GPT/Claude) │    │  (결과 저장)  │              │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘              │
│         │ RateLimiter+Retry │                   │                        │
│         └─────────┬─────────┘                   │                        │
│                   ▼                             │                        │
│         ┌────────────────┐                      │                        │
│         │   Redis Cache  │  ← KIS/LLM 응답 캐싱, KIS 토큰 보관          │
│         └────────┬───────┘                      │                        │
│                  │                              │                        │
│         ┌───────▼────────┐                      │                        │
│         │  Kafka Queue   │  ← 비동기 분석 처리   │                        │
│         └────────┬───────┘                      │                        │
│                  │                              │                        │
│         ┌───────▼────────┐                      │                        │
│         │  Consumer      │                      │                        │
│         │  + DLT/DLQ    │  ← 실패 시 Dead Letter Topic                 │
│         └────────────────┘                      │                        │
│                                                  │                        │
└──────────────────────────────────────────────────────────────────────────┘
```

## 🛠️ 기술 스택

- **Language**: Java 17
- **Framework**: Spring Boot 4.0.2
- **Build**: Gradle
- **Database**: MySQL 8.0
- **Cache**: Redis
- **Message Queue**: Apache Kafka
- **HTTP Client**: WebClient (Reactive)
- **Resilience**: Resilience4j (RateLimiter, Retry)
- **LLM**: OpenAI GPT-4o-mini, Claude Sonnet 4

## 📁 프로젝트 구조

```
src/main/java/com/stock/stockserver/
├── application/                  # 비즈니스 로직
│   ├── StockAnalysisService.java       # 분석 서비스
│   ├── StockDataCollectionService.java # 데이터 수집 (국내/해외)
│   ├── AnalysisResultSaveService.java  # 분석 결과 저장 (트랜잭션 분리)
│   ├── AnalysisRequestPublisher.java   # Kafka publish 인터페이스
│   └── DltRetryService.java            # DLT 재시도 서비스
├── domain/                       # 도메인 모델
│   ├── AnalysisTarget.java             # DOMESTIC / OVERSEAS / ALL
│   ├── AnalysisStatus.java             # 분석 상태 enum
│   ├── RecommendationStatus.java       # 투자 의견 enum
│   ├── entity/
│   │   ├── AnalysisJob.java
│   │   ├── LLMAnalysisResult.java
│   │   ├── StockData.java
│   │   ├── DailyPrice.java
│   │   └── FailedAnalysisRequest.java
│   └── repository/                    # JPA Repository
├── dto/                          # 데이터 전송 객체
├── infrastructure/               # 인프라스트럭처
│   ├── config/
│   │   ├── AsyncConfig.java            # 분리된 스레드 풀 정의
│   │   ├── WebClientConfig.java
│   │   ├── RedisCacheConfig.java
│   │   ├── KafkaConfig.java            # Producer/Consumer/DLT
│   │   └── KisResilienceConfig.java    # KIS RateLimiter + Retry 빈
│   ├── strategy/                       # LLM Strategy 패턴
│   │   ├── LLMAnalysisStrategy.java
│   │   ├── GPTAnalysisStrategy.java
│   │   ├── ClaudeAnalysisStrategy.java
│   │   └── LLMAnalysisPromptBuilder.java
│   ├── consumer/
│   │   ├── AnalysisConsumer.java
│   │   └── DltMessageProcessor.java
│   ├── producer/
│   │   └── KafkaAnalysisRequestPublisher.java
│   └── external/
│       ├── KisApiClient.java           # 국내/해외 통합 클라이언트
│       └── LLMApiClient.java
└── presentation/
    ├── StockController.java
    └── AnalysisController.java
```

## 📡 API Endpoints

### 분석 관련

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/analysis/run?target={ALL\|DOMESTIC\|OVERSEAS}` | 통합 분석 실행 (기본 ALL) |
| POST | `/api/analysis/run/domestic` | 국내 주식 분석 |
| POST | `/api/analysis/run/overseas` | 해외 주식 분석 |
| GET | `/api/analysis/status/{id}` | 분석 상태 조회 |
| GET | `/api/analysis/result/{id}` | 분석 결과 조회 |
| GET | `/api/analysis/latest` | 최근 분석 결과 조회 |

### 주식 데이터

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/stocks/volume-rank` | 거래량 Top 10 조회 |
| GET | `/api/stocks/daily/{code}` | 특정 종목 일봉 조회 |

## 💡 LLM Provider Strategy 패턴

### 문제 상황
- LLM API는 외부 서비스로 불안정할 수 있음
- 특정 Provider 장애 시 서비스 전체 중단 위험
- 새로운 LLM Provider 추가 시 코드 변경 필요

### 해결 방안: Strategy 패턴 + Fallback

```java
public interface LLMAnalysisStrategy {
    String analyze(StockDataDto stockData);
    String getProviderName();
}

@Component("gptStrategy")
public class GPTAnalysisStrategy implements LLMAnalysisStrategy { ... }

@Component("claudeStrategy")
public class ClaudeAnalysisStrategy implements LLMAnalysisStrategy { ... }

public LLMAnalysisResponseDto analyzeStock(StockDataDto stockData) {
    LLMAnalysisStrategy primaryStrategy = strategies.get(provider);
    try {
        return executeAnalysis(primaryStrategy, stockData);
    } catch (Exception e) {
        LLMAnalysisStrategy fallbackStrategy = getFallbackStrategy(primaryStrategy);
        return executeAnalysis(fallbackStrategy, stockData);
    }
}
```

### Fallback 동작 흐름

```
GPT 분석 요청
    │
    ├─ 성공 → 결과 반환 ✅
    │
    └─ 실패 → Claude Fallback 시도
                  │
                  ├─ 성공 → 결과 반환 ✅
                  │
                  └─ 실패 → ERROR 반환 + DLT로 라우팅
```

---

## 📈 성능 개선 스토리

### 문제 상황

초기 구현에서 `/api/analysis/run` API의 응답 시간이 **2~3분**이 걸렸습니다.

```
[문제]
- 순차적 API 호출 (KIS 10개 + LLM 10개)
- Blocking WebClient 사용
- 개별 트랜잭션 처리
```

### 해결 방안

#### 1. 비동기 병렬 처리

```java
List<CompletableFuture<LLMAnalysisResult>> futures = stockDataList.stream()
    .map(stockData -> CompletableFuture.supplyAsync(() ->
        llmApiClient.analyzeStock(stockData),
        llmApiExecutor
    ))
    .collect(Collectors.toList());
```

#### 2. 역할별 분리 스레드 풀 (`AsyncConfig`)

```java
@Bean(name = "llmApiExecutor")
public Executor llmApiExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.setQueueCapacity(100);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    return executor;
}
```

세 풀(`analysisExecutor`, `kisApiExecutor`, `llmApiExecutor`)을 분리하여 한 단계의 지연이 다른 단계를 굶기지 못하게 했고, `CallerRunsPolicy`로 백프레셔를 위쪽으로 흘려보냅니다.

#### 3. CompletableFuture 타임아웃

```java
List<LLMAnalysisResult> results = futures.stream()
    .map(f -> f.orTimeout(60, TimeUnit.SECONDS))
    .map(CompletableFuture::join)
    .filter(Objects::nonNull)
    .collect(Collectors.toList());
```

### 개선 결과

| 구분 | 개선 전 | 개선 후 | 개선율 |
|------|--------|--------|--------|
| KIS API 호출 | 20-50초 | 3-5초 | **90%** |
| LLM API 호출 | 100-150초 | 15-20초 | **87%** |
| **총 소요 시간** | **128-217초** | **18-25초** | **85%** |

---

## 🚀 Redis 캐싱

### 문제 상황
- KIS API 호출 제한
- 동일한 분석결과 중복 호출로 인한 LLM 비용 증가

### 해결 방안

```java
@Cacheable(cacheNames = "llmAnalysisCache",
    key = "#stockData.stockCode() + '_' + T(java.time.LocalDate).now().toString()")
public LLMAnalysisResponseDto analyzeStock(StockDataDto stockData) { ... }
```

### 캐시 설정

| 캐시 이름 | 용도 | TTL |
|-----------|------|-----|
| `kisVolumeRankCache` / `kisDomesticVolumeRankCache` / `kisOverseasVolumeRankCache` | 거래량 Top 10 | 10분 |
| `kisDailyCache` / `kisDomesticDailyCache` / `kisOverseasDailyCache` | 일봉 데이터 | 1시간 |
| `llmAnalysisCache` | LLM 분석 결과 (`stockCode + today` 키) | 1시간 |

KIS access token도 Redis에 6시간 TTL로 캐시되어 토큰 재발급 트래픽을 최소화합니다.

---

## 🔄 Kafka 기반 비동기 처리

### 문제 상황
- 분석 API 요청 시 처리 완료까지 **18-25초** 응답 대기
- `@Async` 분리 시 Kafka ACK 시점이 모호하여 서버 재시작 시 메시지 유실

### 해결 방안: Kafka Message Queue + 수동 ACK

```
API 요청 → Kafka Producer → [analysis-requests 토픽] → Consumer → 분석 완료 → ACK
    ↑                                                                        │
    └──────────────────── 즉시 응답 (< 100ms) ──────────────────────────────┘
```

Consumer는 분석 로직을 동기 실행하고 완료 후 ACK를 커밋(`AckMode.MANUAL`, concurrency=3). 서버 재시작 시 ACK되지 않은 메시지는 Kafka가 자동 재배달합니다.

### 도입 효과

| 구분 | 도입 전 | 도입 후 | 효과 |
|------|--------|--------|------|
| **API 응답 시간** | 18-25초 | **< 100ms** | **즉시 응답** |
| **서버 재시작** | 메시지 유실 | **ACK 기반 재배달** | **안정성** |
| **확장성** | 단일 서버 | **수평 확장** | **처리량 증가** |

---

## 💀 DLT (Dead Letter Topic) 처리

### 문제 상황
- 기존 구조에서 LLM·KIS API 분석 실패 시 예외가 내부에서 처리되어 DLT 파이프라인이 우회됨
- JSON 파싱 오류, DB 장애 등 일부 실패만 DLT를 타는 불일관한 에러 처리

### 해결 방안

분석 실패 시 예외를 외부로 전파하여 모든 장애 유형이 동일한 파이프라인을 타도록 개선

```
analysis-requests 토픽
        │
        ├─ Consumer 처리 성공 → ACK → DB 저장 ✅
        │
        └─ Consumer 처리 실패 (LLM/KIS/JSON 등)
                    │
                    └─ DefaultErrorHandler: 1초 간격 3회 재시도
                                │
                                ▼
                 analysis-requests.DLT 토픽
                                │
                                ▼
                    DltMessageProcessor가 메시지 수신
                                │
                                ├─ DB에 실패 기록 저장
                                │
                                └─ DltRetryService가 5분마다 재시도 (최대 3회)
```

### DLT 재시도 로직

```java
@Scheduled(initialDelay = 60000, fixedDelay = 300000)  // 5분마다
public void retryFailedRequests() {
    List<FailedAnalysisRequest> failedRequests =
        failedAnalysisRequestRepository.findByProcessedFalse();

    for (FailedAnalysisRequest request : failedRequests) {
        if (request.getRetryCount() < MAX_RETRY_COUNT) {
            kafkaTemplate.send(ANALYSIS_TOPIC, analysisId, message).get();
            request.incrementRetryCount();
            request.markAsProcessed();
        }
    }
}
```

---

## 🌍 해외주식 분석 도입 & KIS API 쓰로틀링 트러블슈팅

### 문제

기존에는 국내(KRX) 주식만 분석하고 있었으나, 해외(NAS/NYS/AMS) 주식 분석을 도입하면서 KIS API 호출량이 급증했습니다.

| 항목 | 도입 전 (국내) | 도입 후 (국내 + 해외) |
|---|---|---|
| 거래량 순위 호출 | 1회 | **4회** (국내 1 + 해외 거래소 3) |
| 일봉 호출 | 10회 | **20회** |
| **분석 1 cycle 총 호출** | 11 req | **24 req** |

> KIS 해외 거래량 순위 API는 거래소 단위 조회라 NAS/NYS/AMS를 각각 호출한 뒤 합산해야 합니다.

증상:
- `kisApiExecutor`(core 10/max 50)에서 일봉 조회가 동시에 터지면서 **HTTP 500 / `EGW00201`(초당 거래건수 초과)** 발생
- 일부 종목 일봉이 비어 있는 채 LLM 단계로 넘어가 `LLMAnalysisResult` 누락
- 단발성 5xx/429에도 재시도가 없어 그대로 실패 확정

제약 조건:
- KIS Open API 실전계좌 한도: **초당 20건**
- 단일 JVM 인스턴스 운영이므로 프로세스 로컬 RateLimiter로 충분 (스케일아웃 시 분산 RateLimiter 도입 필요)

### 해결 (Action)

**Resilience4j 도입 — RateLimiter와 Retry의 책임 분리**

| 컴포넌트 | 책임 |
|---|---|
| `RateLimiter` | 호출 시점을 늦춰 KIS 한도(20 rps)를 **선제적으로 넘기지 않도록 차단** |
| `Retry` | 한도 초과로 받은 5xx/429에 대해 **지수 백오프 재시도** |

#### 스펙 정의 (`application.yml`)

```yaml
kis:
  api:
    resilience:
      rate-limit:
        limit-for-period: 15        # 1초당 15건 (한도 20 대비 75% 사용, 헤드룸 25%)
        refresh-period-ms: 1000     # fixed-window: 매 1초마다 permit 일괄 리필
        timeout-ms: 5000            # permit 대기 큐잉 한도
      retry:
        max-attempts: 3             # 최초 호출 + 2회 재시도
        initial-wait-ms: 300        # 1차 대기 300ms
        multiplier: 2.0             # 300 → 600 → 1200ms
```

산정 근거:
- **15 req/s**: 24 req를 약 1.6초에 소화 가능. 토큰 발급 등 공용 트래픽 여유 확보
- **timeout 5s**: 워스트 케이스(24 req / 15 rps ≈ 2s + 토큰 갱신 충돌 1s + Retry 누적 백오프 2.1s ≈ 5.1s)에 맞춘 큐잉 상한
- **재시도 3회 / 지수 백오프**: 누적 약 2.1초로 LLM 60초 `orTimeout` 안에 충분히 종결

#### 재시도 대상 좁히기

```java
private boolean isRetryable(Throwable throwable) {
    if (throwable instanceof WebClientResponseException e) {
        return e.getStatusCode().is5xxServerError()
                || e.getStatusCode().value() == 429;
    }
    return false;
}
```

4xx(400/401/403)는 재시도해도 동일 실패 → 즉시 컨슈머로 전파 → DLT 라우팅. 5xx/429만 인라인 재시도.

#### 데코레이터 적용 — 순서가 중요

```java
// KisApiClient#callApi
String responseBody = Retry.decorateSupplier(kisApiRetry,           // 바깥
        RateLimiter.decorateSupplier(kisRateLimiter,                // 안
                () -> executeApiCall(method, fullUrl, endpoint, trId, accessToken)
        )
).get();
```

호출 흐름:

```
.get() 호출
  └─ Retry 시작
       ├─ 시도 1 → RateLimiter permit 차감 → executeApiCall() → 429
       ├─ 300ms 대기
       ├─ 시도 2 → RateLimiter permit 차감 → executeApiCall() → 200 OK
       └─ 결과 반환
```

핵심: **재시도가 일어날 때마다 RateLimiter를 다시 통과**하여 permit이 재차감됩니다. 만약 순서를 뒤집으면 RateLimiter는 1번만 통과되고 재시도 폭주가 한도를 그대로 부수게 되므로, **Retry(바깥) → RateLimiter(안)** 가 정답입니다.

#### 설정 외부화 + 관측

- 모든 임계값을 `@Value`로 외부화 → YAML 수정만으로 운영 중 한도 조정 가능
- `RateLimiter.onFailure`, `Retry.onRetry/onError` 이벤트를 모두 로깅 → 언제·몇 번째 시도에서·어떤 에러로 재시도했는지 가시화

### 결과

| 지표 | Before | After |
|---|---|---|
| KIS 5xx/429 발생 비율 | 분석 1회당 평균 3~5건 | **0건** |
| 일봉 누락으로 인한 LLM 분석 스킵 | 평균 2건/cycle | **0건** |
| 분석 1회 평균 소요시간 | 약 18초 (실패 무시) | 약 22초 (재시도 흡수, 전 종목 성공) |
| 한도 변경 시 재배포 | 코드 수정 | **YAML 수정만** |

부수 효과:
- 책임이 명확히 분리되어 LLM API에도 동일 패턴을 그대로 이식 가능
- `callApi` 한 군데에만 데코레이터를 적용했으므로 신규 KIS 엔드포인트가 추가돼도 자동 보호
- 4xx는 즉시 실패하여 DLT 경로가 살아 있으므로 **회복 가능한 에러는 인라인 재시도, 회복 불가능한 에러는 DLT** 라는 2단 방어선 완성

---

## ⚙️ 설정

### 환경 변수

```bash
# KIS API
KIS_APP_KEY=your_app_key
KIS_APP_SECRET=your_app_secret

# LLM API
OPENAI_API_KEY=your_openai_api_key
CLAUDE_API_KEY=your_claude_api_key
```

### application.yml 주요 설정

```yaml
llm:
  provider: gpt  # primary provider: gpt 또는 claude
  gpt:
    model: gpt-4o-mini
    max-tokens: 2000
  claude:
    model: claude-sonnet-4-20250514
    max-tokens: 2000

analysis:
  top-stocks: 10
  days-back: 60
  overseas:
    exchanges: NAS,NYS,AMS

server:
  shutdown: graceful   # 처리 중인 Kafka 메시지 소화 후 종료
```

### 로컬 인프라 요구사항

- MySQL 3306 (`stock_analysis`, `root`/`1234`)
- Redis 6379
- Kafka 9092

## 🏃 실행

```bash
# 빌드
./gradlew build

# 실행 (port 8080)
./gradlew bootRun

# 전체 테스트
./gradlew test

# 단일 테스트 클래스
./gradlew test --tests "LLMApiClientTest"
```

## 🔒 보안 주의사항

- API 키는 환경 변수로 관리
- CORS 설정 시 신뢰할 수 있는 도메인만 허용
- `application.yml`의 DB 자격증명은 로컬 전용 — 프로덕션에서는 환경 변수로 분리
- 프로덕션에서는 `spring.jpa.hibernate.ddl-auto: create` 를 반드시 변경
