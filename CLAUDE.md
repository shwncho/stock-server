# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

- `./gradlew bootRun` — run the server on port 8080
- `./gradlew build` — compile, test, package
- `./gradlew test` — run JUnit Platform tests
- `./gradlew test --tests "LLMApiClientTest"` — run a single test class
- `./gradlew test --tests "LLMApiClientTest.someMethod"` — run a single test method

Local runtime requires MySQL (3306, db `stock_analysis`, user `root`/`1234`), Redis (6379), and Kafka (9092). Required env vars: `KIS_APP_KEY`, `KIS_APP_SECRET`, `OPENAI_API_KEY`, `CLAUDE_API_KEY`.

## Architecture

Java 17 + Spring Boot 4.0.2. The codebase is a layered application (`presentation` → `application` → `domain` ← `infrastructure`); do not place infrastructure concerns in `domain`, and keep controller logic out of `application`.

### Request flow for `/api/analysis/run`

The endpoint returns within ~100ms by handing the work to Kafka rather than executing inline:

1. `AnalysisController` calls `AnalysisRequestPublisher.publish(analysisId)` (impl: `KafkaAnalysisRequestPublisher`) and `StockAnalysisService.saveJob` (status `RUNNING`).
2. `AnalysisConsumer` reads from topic `analysis-requests` and runs `StockAnalysisService.runFullAnalysis` synchronously, only ACKing after success — so a restart mid-analysis causes Kafka to redeliver instead of losing the message. Listener container uses `AckMode.MANUAL`, concurrency 3.
3. Inside `runFullAnalysisInternal`: `StockDataCollectionService` parallel-fetches KIS data on `kisApiExecutor`, then each stock is sent to `LLMApiClient.analyzeStock` via `CompletableFuture.supplyAsync(..., llmApiExecutor)` with a 60s `orTimeout`. Failed/timed-out stocks are filtered out — partial success is normal.
4. Results are persisted in a separate transaction via `AnalysisResultSaveService.saveAll` so that the orchestration thread is not the transaction owner.

### Failure handling (DLT)

Consumer failures propagate out of `AnalysisConsumer` so the `DefaultErrorHandler` can route them. Configured in `KafkaConfig`:

- `FixedBackOff(1000ms, 3)` retries, then publish to `analysis-requests.DLT` via `DeadLetterPublishingRecoverer`.
- `DltMessageProcessor` consumes the DLT and writes a `FailedAnalysisRequest` row.
- `DltRetryService` is a `@Scheduled` job (every 5 min, max 3 attempts) that re-publishes failed requests to `analysis-requests` and marks `processed=true` only after the producer `.get()` confirms delivery.

When changing analysis logic, ensure exceptions still escape `AnalysisConsumer` — swallowing them inside the consumer bypasses retry+DLT entirely (this was the explicit fix in the current design; do not regress it).

### LLM Strategy + Fallback

`LLMApiClient` selects a `LLMAnalysisStrategy` bean by `llm.provider` (`gpt` or `claude`). On primary failure it falls back to the other strategy. To add a provider: implement `LLMAnalysisStrategy` and register it as a Spring bean — no client changes needed. Prompt construction is centralized in `LLMAnalysisPromptBuilder`.

### Thread pools (AsyncConfig)

Three named executors are intentionally separate so that one slow stage cannot starve the others:
- `analysisExecutor` (core 3 / max 6) — orchestration
- `kisApiExecutor` (core 10 / max 50) — KIS calls
- `llmApiExecutor` (core 10 / max 50) — LLM calls

All use `CallerRunsPolicy` so backpressure pushes back on the producer rather than dropping work. Always inject by `@Qualifier`/bean name.

### Caching

Redis-backed Spring Cache. TTLs (set in `RedisCacheConfig`):
- `kisVolumeRankCache` — 10 min
- `kisDailyCache` — 1 hour
- `llmAnalysisCache` — 1 hour, keyed by `stockCode + '_' + today` so results are deduped per trading day.

### HTTP client

All outbound HTTP (KIS, OpenAI, Claude) goes through `WebClient` configured in `WebClientConfig`. The OkHttp dependency was removed — do not reintroduce it.

## Configuration caveats

- `spring.jpa.hibernate.ddl-auto: create` recreates the schema on every boot. Change this before pointing at any persistent/shared DB.
- `application.yml` checks in real DB credentials (`root`/`1234`) for local dev — treat as local-only.
- Server uses `shutdown: graceful` so in-flight Kafka consumers finish before exit.

## Testing

Spring Boot Test + JUnit Platform. Mock external dependencies (KIS, OpenAI, Claude, Redis, Kafka, MySQL) in unit tests; reserve `@SpringBootTest` for cases where wiring/framework behavior is the thing under test. Tests mirror the main package layout under `src/test/java/com/stock/stockserver/`.

## Commit style

Conventional Commit prefixes (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`) with Korean descriptions are the norm in this repo — match the existing style.
