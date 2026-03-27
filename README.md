# Stock Analysis Server

한국투자증권(KIS) API와 LLM(GPT/Claude)을 연동하여 주식 종목을 분석하는 백엔드 서비스입니다.

## 🚀 주요 기능

- **거래량 Top 10 조회**: 한국투자증권 API를 통해 거래량 상위 종목을 조회합니다.
- **일봉 데이터 수집**: 각 종목의 60일 일봉 데이터를 수집합니다.
- **LLM 분석**: GPT-4o-mini 또는 Claude를 활용한 기술적 분석을 수행합니다.
- **투자 의견 생성**: 매수/매도/보유 의견을 자동 생성합니다.
- **LLM Fallback**: Primary LLM 실패 시 Secondary LLM로 자동 전환

## 🏗️ 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Stock Analysis Server                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐              │
│  │   KIS API    │    │   LLM API    │    │   MySQL      │              │
│  │  (주식 데이터) │    │ (GPT/Claude) │    │  (결과 저장)  │              │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘              │
│         │                   │                   │                        │
│         └─────────┬─────────┘                   │                        │
│                   ▼                             │                        │
│         ┌────────────────┐                      │                        │
│         │   Redis Cache  │  ← KIS/API 응답, LLM 분석 결과 캐싱         │
│         └────────┬───────┘                      │                        │
│                  │                              │                        │
│         ┌───────▼────────┐                      │                        │
│         │  Kafka Queue   │  ← 비동기 분석 처리   │                        │
│         └────────┬───────┘                      │                        │
│                  │                              │                        │
│         ┌───────▼────────┐                      │                        │
│         │  Consumer      │                      │                        │
│         │  + DLT/DLQ    │  ← 실패 시 Dead Letter Topic               │
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
- **API Client**: WebClient (Reactive)
- **LLM**: OpenAI GPT-4o-mini, Claude-sonnet-4

## 📁 프로젝트 구조

```
src/main/java/com/stock/stockserver/
├── application/                  # 비즈니스 로직
│   ├── StockAnalysisService.java       # 분석 서비스
│   ├── StockDataCollectionService.java # 데이터 수집
│   └── DltRetryService.java            # DLT 재시도 서비스
├── domain/                       # 도메인 모델
│   ├── entity/
│   │   ├── AnalysisJob.java           # 분석 작업 entity
│   │   ├── LLMAnalysisResult.java      # LLM 분석 결과
│   │   ├── StockData.java              # 주식 데이터
│   │   ├── DailyPrice.java             # 일봉 데이터
│   │   └── FailedAnalysisRequest.java   # 실패한 요청
│   ├── repository/                    # JPA Repository
│   ├── AnalysisStatus.java             # 분석 상태 enum
│   └── RecommendationStatus.java       # 투자 의견 enum
├── dto/                          # 데이터 전송 객체
│   ├── StockDataDto.java
│   ├── VolumeRankDto.java
│   ├── DailyPriceDto.java
│   ├── LLMAnalysisResponseDto.java
│   └── ...
├── infrastructure/               # 인프라스트럭처
│   ├── config/
│   │   ├── AsyncConfig.java           # 비동기 설정
│   │   ├── WebClientConfig.java        # WebClient 설정
│   │   ├── RedisCacheConfig.java       # Redis 캐시 설정
│   │   ├── KafkaConfig.java            # Kafka 설정
│   │   └── ...
│   ├── strategy/                      # LLM Strategy 패턴
│   │   ├── LLMAnalysisStrategy.java    # Strategy 인터페이스
│   │   ├── GPTAnalysisStrategy.java   # GPT 구현체
│   │   ├── ClaudeAnalysisStrategy.java # Claude 구현체
│   │   └── LLMAnalysisPromptBuilder.java
│   ├── consumer/
│   │   ├── AnalysisConsumer.java      # Kafka Consumer
│   │   └── DltMessageProcessor.java    # DLT 메시지 처리
│   └── external/
│       ├── KisApiClient.java          # KIS API 클라이언트
│       └── LLMApiClient.java           # LLM API 클라이언트
└── presentation/                 # API 엔드포인트
    ├── StockController.java
    └── AnalysisController.java
```

## 💡 LLM Provider Strategy 패턴

### 문제 상황
- LLM API는 외부 서비스로 불안정할 수 있음
- 특정 Provider 장애 시 서비스 전체 중단 위험
- 새로운 LLM Provider 추가 시 코드 변경 필요

### 해결 방안: Strategy 패턴 + Fallback

```java
// Strategy 인터페이스
public interface LLMAnalysisStrategy {
    String analyze(StockDataDto stockData);
    String getProviderName();
}

// 구현체
@Component("gptStrategy")
public class GPTAnalysisStrategy implements LLMAnalysisStrategy { ... }

@Component("claudeStrategy")  
public class ClaudeAnalysisStrategy implements LLMAnalysisStrategy { ... }

// Client에서 Fallback 처리
public LLMAnalysisResponseDto analyzeStock(StockDataDto stockData) {
    LLMAnalysisStrategy primaryStrategy = strategies.get(provider);
    
    try {
        return executeAnalysis(primaryStrategy, stockData);
    } catch (Exception e) {
        // Fallback: Primary 실패 시 Secondary 시도
        LLMAnalysisStrategy fallbackStrategy = getFallbackStrategy(primaryStrategy);
        return executeAnalysis(fallbackStrategy, stockData);
    }
}
```

### 장점

| 구분 | 설명 |
|------|------|
| **유연성** | 새로운 LLM Provider 추가 시 Strategy 구현만 하면 됨 |
| **확장성** | 코드 변경 없이 Bean 추가만으로 확장 |
| **안정성** | Primary 실패 시 Fallback으로 자동 전환 |
| **테스트 용이성** | 각 Strategy 단위 테스트 가능 |

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

## 📡 API Endpoints

### 분석 관련

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/analysis/run` | 전체 분석 실행 |
| GET | `/api/analysis/status/{id}` | 분석 상태 조회 |
| GET | `/api/analysis/result/{id}` | 분석 결과 조회 |
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
llm:
  provider: gpt  # primary provider: gpt 또는 claude
  gpt:
    model: gpt-4o-mini
    max-tokens: 2000
  claude:
    model: claude-sonnet-4-20250514
    max-tokens: 1024
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

### 개선 결과

| 구분 | 개선 전 | 개선 후 | 개선율 |
|------|--------|--------|--------|
| KIS API 호출 | 20-50초 | 3-5초 | **90%** |
| LLM API 호출 | 100-150초 | 15-20초 | **87%** |
| **총 소요 시간** | **128-217초** | **18-25초** | **85%** |

---

## 🚀 추가 개선: Redis 캐싱

### 문제 상황

- **KIS API 호출 제한**: 외부 API 호출 지연
- **LLM 비용**: 동일한 분석결과 중복 호출로 인한 비용 증가

### 해결 방안: Redis 기반 캐싱

```java
// LLM 분석 결과 캐싱 (시간 단위)
@Cacheable(cacheNames = "llmAnalysisCache", 
    key = "#stockData.stockCode() + '_' + T(java.time.LocalDateTime).now().getHour()")
public LLMAnalysisResponseDto analyzeStock(StockDataDto stockData) { ... }
```

### 캐시 설정

| 캐시 이름 | 용도 | TTL |
|-----------|------|-----|
| `kisVolumeRankCache` | 거래량 Top 10 | 10분 |
| `kisDailyCache` | 일봉 데이터 | 1시간 |
| `llmAnalysisCache` | LLM 분석 결과 | 1시간 |

---

## 🔄 Kafka 기반 비동기 처리

### 문제 상황

- 분석 API 요청 시 처리 완료까지 **18-25초** 동안 응답 대기
- 서버 재시작 시 진행 중인 분석 작업 손실

### 해결 방안: Kafka Message Queue

```
API 요청 → Kafka Producer → [analysis-requests 토픽] → Consumer → 분석 로직
    ↑                                                        │
    └──────────────────── 즉시 응답 ──────────────────────────┘
```

### Kafka 도입 효과

| 구분 | 도입 전 | 도입 후 | 효과 |
|------|--------|--------|------|
| **API 응답 시간** | 18-25초 | **< 100ms** | **즉시 응답** |
| **서버 재시작** | 작업 손실 | **메시지 보존** | **안정성** |
| **확장성** | 단일 서버 | **수평 확장** | **처리량 증가** |

---

## 💀 DLT (Dead Letter Topic) 처리

### 문제 상황

- LLM API 실패 시 재시도에도 불구하고 영구 실패
- 실패한 메시지 추적 및 재처리 필요

### 해결 방안

```
-analysis-requests 토픽
        │
        ├─ Consumer 처리 성공 → DB 저장 ✅
        │
        └─ Consumer 처리 실패 (3회 재시도)
                    │
                    ▼
         analysis-requests.DLT 토픽 (Dead Letter)
                    │
                    ▼
        DltMessageProcessor가 메시지 수신
                    │
                    ├─ DB에 실패 기록 저장
                    │
                    └─ DltRetryService가 5분마다 재시도
```

### DLT 재시도 로직

```java
@Scheduled(initialDelay = 60000, fixedDelay = 300000)  // 5분마다 실행
public void retryFailedRequests() {
    List<FailedAnalysisRequest> failedRequests = 
        failedAnalysisRequestRepository.findByProcessedFalse();
    
    for (FailedAnalysisRequest request : failedRequests) {
        if (request.getRetryCount() < MAX_RETRY_COUNT) {
            kafkaTemplate.send(ANALYSIS_TOPIC, analysisId, message);
            request.incrementRetryCount();
        }
    }
}
```

### 실패 요청 관리

| 구분 | 설명 |
|------|------|
| 실패 기록 | `failed_analysis_requests` 테이블 저장 |
| 재시도 횟수 | 최대 3회 |
| 재시도 주기 | 5분마다 스케줄러 실행 |
| 상태 추적 | `processed` 필드로 처리 완료 여부 |

---

## 🔒 보안 주의사항

- API 키는 환경 변수로 관리
- CORS 설정 시 신뢰할 수 있는 도메인만 허용
- 프로덕션에서는 `ddl-auto: create` 변경

---

## 🧪 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests "LLMApiClientTest"

# 테스트 리포트 확인
./gradlew test --info
```
