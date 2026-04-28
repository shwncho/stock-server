# Repository Guidelines

## Project Structure & Module Organization

This is a Java 17 Spring Boot stock analysis server. Main code lives in `src/main/java/com/stock/stockserver/`:

- `presentation/`: REST controllers such as `StockController` and `AnalysisController`.
- `application/`: business workflows, analysis orchestration, publishing, and retry services.
- `domain/`: entities, enums, repositories, and domain storage abstractions.
- `dto/`: request/response and event DTOs.
- `infrastructure/`: external API clients, Kafka consumers/producers, Redis/JPA/config, and LLM strategies.

Tests are under `src/test/java/com/stock/stockserver/` and mirror the main packages. Configuration is in `src/main/resources/application.yml`.

## Build, Test, and Development Commands

- `./gradlew bootRun`: run the server locally on port `8080`.
- `./gradlew test`: run the JUnit test suite.
- `./gradlew build`: compile, test, and package the application.
Local runtime dependencies are MySQL, Redis, and Kafka using the hosts/ports in `application.yml`.

## Coding Style & Naming Conventions

Use Java 17 and Spring idioms already present in the codebase. Keep packages aligned with the existing layers; do not place infrastructure concerns in `domain` or controller logic in `application`. Use 4-space indentation and descriptive role-based class names, for example `StockAnalysisService`, `KisApiClient`, or `KafkaAnalysisRequestPublisher`.

DTO classes use `Dto`, repositories use `Repository`, and tests use `*Test`. Prefer constructor injection and existing Spring configuration classes.

## Testing Guidelines

The project uses Spring Boot Test with JUnit Platform. Add or update tests in the matching `src/test/java` package for behavior changes. Keep unit tests focused on domain/application behavior, and use Spring context tests only when wiring or framework behavior is part of the change.

Run `./gradlew test` before opening a PR. Mock KIS, OpenAI, Claude, Redis, Kafka, and MySQL in unit tests.

## Commit & Pull Request Guidelines

Recent commits use Conventional Commit-style prefixes, often with Korean descriptions, such as `refactor: kafka 설정 Config 클래스로 취합`, `test: LLMApi test 코드 수정`, and `docs: readme 수정`. Use a short imperative subject with a prefix like `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, or `chore:`.

Pull requests should include a brief summary, test results such as `./gradlew test`, related issue links when available, and notes about configuration or migration impact.

## Security & Configuration Tips

Do not commit secrets. `KIS_APP_KEY`, `KIS_APP_SECRET`, `OPENAI_API_KEY`, and `CLAUDE_API_KEY` must come from the environment. Be careful with `spring.jpa.hibernate.ddl-auto: create`, which recreates schema on startup; change it before using persistent shared data.
