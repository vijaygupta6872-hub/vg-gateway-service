# Gateway Service

Gateway Service accepts account event requests, forwards new events to Account Service as transactions, and persists successfully applied events. It provides idempotency by `eventId`, trace ID propagation, basic health reporting, H2-backed local persistence, and metrics through Spring Boot Actuator.

## Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/events` | Creates an event. Returns `201 Created` for a new event and `200 OK` when the `eventId` already exists. |
| `GET` | `/events/{id}` | Returns an event by `eventId`. Returns `404` when not found. |
| `GET` | `/events?account={accountId}` | Lists events for an account ordered by `eventTimestamp` ascending. |
| `GET` | `/health` | Returns simple Gateway health JSON with service name and status. |
| `GET` | `/actuator/health` | Spring Boot Actuator health endpoint. |
| `GET` | `/actuator/metrics` | Spring Boot Actuator metrics endpoint. |

## Configuration

Configuration is in `src/main/resources/application.properties`.

Key properties:

```properties
spring.application.name=gateway-service
spring.datasource.url=jdbc:h2:mem:gateway-service;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
spring.jpa.hibernate.ddl-auto=create
management.endpoints.web.exposure.include=health,metrics
account-service.base-url=http://localhost:8081
account-service.connect-timeout=2s
account-service.read-timeout=3s
resilience4j.circuitbreaker.instances.accountService.sliding-window-type=COUNT_BASED
resilience4j.circuitbreaker.instances.accountService.sliding-window-size=5
resilience4j.circuitbreaker.instances.accountService.minimum-number-of-calls=3
resilience4j.circuitbreaker.instances.accountService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.accountService.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.accountService.permitted-number-of-calls-in-half-open-state=2
```

## Run Locally

Start Account Service on `http://localhost:8081`, then run Gateway Service:

```powershell
.\mvnw.cmd spring-boot:run
```

Gateway starts on the default Spring Boot port `8080`.

H2 console:

```text
http://localhost:8080/h2-console
```

Use JDBC URL:

```text
jdbc:h2:mem:gateway-service
```

## Run Tests

```powershell
.\mvnw.cmd test
```

The test suite includes repository tests, MockMvc API tests, and a MockWebServer integration test for the Account Service HTTP call.

## Trace Propagation

Gateway uses the `X-Trace-Id` header for request tracing.

- If a request includes `X-Trace-Id`, Gateway reuses it.
- If the header is missing, Gateway generates a UUID.
- The trace ID is stored in MDC as `traceId`.
- Gateway returns `X-Trace-Id` on HTTP responses.
- Outbound Account Service calls include the same `X-Trace-Id`.

## Resiliency

Calls to Account Service are protected with a Resilience4j circuit breaker through Spring Cloud CircuitBreaker. The circuit breaker name is:

```text
accountService
```

Gateway wraps connection failures, timeouts, open-circuit failures, and Account Service `5xx` responses as `AccountServiceUnavailableException`.

When Account Service is unavailable:

- `POST /events` returns `503 Service Unavailable`.
- The response body is a JSON `ErrorResponse` containing `traceId`, status, error, message, and timestamp.
- The event is not persisted.

## Metrics

Custom Gateway counters are exposed through Actuator metrics:

- `gateway.events.accepted`
- `gateway.events.duplicates`
- `gateway.events.account_service_failures`

Example:

```text
http://localhost:8080/actuator/metrics/gateway.events.accepted
```
