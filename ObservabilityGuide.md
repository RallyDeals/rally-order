# Standard Observability Guide (Tracing + Correlation + Logging)

This guide is the canonical recipe we use in the **rally** services to add distributed
tracing (W3C `traceparent`), cross-service correlation (`X-Correlation-Id`), and structured
log shipping (Loki) — exactly as implemented in the `rally-payment` service.

> **Reference guide to "see it fully working":** `rally-payment` is the blueprint.
> Read `PaymentInitiationListener`, `CorrelationIdFilter`, `KafkaCorrelationIdInterceptor`,
> `OutboxRelay`, `OutboxEventWriter`, `logback-spring.xml`, and the observability block in
> `application.properties`. This document explains *why* and *how* to replicate it.

---

## 1. The contract — what Kafka messages you receive

Every inbound Kafka bus message carries these headers. **You must design your service to
respect and propagate them:**

| Header | Meaning | Required? |
|---|---|---|
| `X-Id` | The message id (often the outbox `message_id` / event id) | yes |
| `X-Type` | The event type used to deserialize/pick the payload type | yes |
| `X-Correlation-Id` | End-to-end business correlation id (UUID) | yes |
| `traceparent` | W3C trace context: `00-<traceId 32 hex>-<spanId 16 hex>-01` | yes (when the producer is tracing-enabled) |

> `X-Causation-Id` and `X-Trace-Id` are optional/additional headers some producers set.
> `traceparent` is the standard one and is auto-emitted by a tracing-enabled KafkaTemplate.

**Goal of the whole system:** a single Kafka message, its HTTP round-trips, the outbox
enqueue, and the relay publish must share **one `traceId`** and **one `X-Correlation-Id`** so
you can trace a business transaction across every service in Jaeger and grep a single value
in Loki.

---

## 2. All required Maven dependencies (pom.xml)

Add **all** of these to `pom.xml`. You need the whole set — omitting any one silently
disables a piece (tracing bridge, OTLP export, or Loki/app log shipping).

```xml
<!-- Structured / JSON logging encoder -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>

<!-- Loki log appender (ships structured logs to Grafana Loki) -->
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>2.0.3</version>
</dependency>

<!-- Actuator: exposes /actuator and drives tracing lifecycle -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer -> OpenTelemetry tracing bridge (produces the traceId/spanId and MDC) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-micrometer-tracing-opentelemetry</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- OpenTelemetry OTLP exporter -> ships spans to Jaeger/OTel collector -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

---

## 3. Application properties — the observability block

Add this block (it is inert-to-active as soon as the pom deps from §2 exist).

```properties
# ===================================================================
# Actuator & Observability & Tracing
# ===================================================================
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=${MANAGEMENT_HEALTH_SHOW_DETAILS:always}
management.tracing.enabled=true
management.tracing.sampling.probability=1.0
management.opentelemetry.tracing.export.otlp.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
management.opentelemetry.tracing.export.otlp.transport=http

# W3C baggage: carry X-Correlation-Id across services and surface it in
# logs (MDC) and as a span tag, so it is searchable in Jaeger by tag.
management.tracing.baggage.remote-fields=X-Correlation-Id
management.tracing.baggage.correlation.fields=X-Correlation-Id
management.tracing.baggage.tag-fields=X-Correlation-Id

# Loki appender endpoint (consumed by logback-spring.xml springProperty)
loki.url=${LOKI_URL:http://localhost:3100/loki/api/v1/push}
```

Also make sure Kafka observation is on so the W3C `traceparent` is auto-propagated on
produce **and** restored on consume:

```properties
spring.kafka.listener.observation-enabled=true
spring.kafka.template.observation-enabled=true
```

> **Why `management.opentelemetry.tracing.*`?** With the §2 deps, OTel produces a root
> `traceparent` for your app, **extracts** an incoming one on HTTP/Kafka (proper propagation),
> and automatically populates the SLF4J MDC keys **`traceId`** and **`spanId`** — so your logs
> carry the trace with zero custom code. `opentelemetry-exporter-otlp` ships the spans to the
> collector endpoint (Jaeger in our infra).

---

## 4. Logback configuration (`logback-spring.xml`)

Add `src/main/resources/logback-spring.xml`. Key points:

- **CONSOLE** is plain text for `local|dev` (readable) and JSON (`LogstashEncoder`) otherwise.
- **ROLLING_FILE** writes JSON logs with MDC `traceId`/`spanId`.
- **LOKI** (`Loki4jAppender`) ships structured metadata including `traceId`, `spanId`,
  `correlationId`, `level`, `logger` so you can `{app="your-service"}` + filter by trace.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <springProfile name="local | dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] [%X{spanId}] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
    </springProfile>

    <springProfile name="!local &amp; !dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        </appender>
    </springProfile>

    <appender name="ROLLING_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/your-service.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/archived/your-service-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
    </appender>

    <springProperty scope="context" name="LOKI_URL" source="loki.url" defaultValue="http://localhost:3100/loki/api/v1/push"/>

    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>${LOKI_URL}</url>
        </http>
        <labels>
            app = your-service            <!-- CHANGE ME -->
            host = ${HOSTNAME}
        </labels>
        <structuredMetadata>
            level = %level
            thread = %thread
            logger = %logger
            traceId = %X{traceId}
            spanId = %X{spanId}
            correlationId = %X{X-Correlation-Id}
        </structuredMetadata>
        <message>
            <pattern>%-5level [%thread] [%X{traceId}] [%X{spanId}] [%X{X-Correlation-Id}] %logger{36} - %msg%n</pattern>
        </message>
        <batch>
            <maxItems>200</maxItems>
            <timeoutMs>5000</timeoutMs>
        </batch>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="ROLLING_FILE"/>
        <appender-ref ref="LOKI"/>
    </root>

</configuration>
```

> **Note the MDC key for correlation is `X-Correlation-Id`** (with dashes) because that is the
> exact key the interceptor/filter put into MDC. Keep it consistent with the Loki
> `correlationId = %X{X-Correlation-Id}` mapping.

---

## 5. How `traceparent` gets auto-injected into MDC

You do **not** parse `traceparent` manually for your own logs. The OTel bridge does it:

1. On **HTTP inbound**: the tracing filter extracts `traceparent`, starts/seeds the span, and
   OTel publishes `traceId` + `spanId` to MDC automatically.
2. On **Kafka inbound**: `spring.kafka.listener.observation-enabled=true` makes the container
   start a child span from the incoming `traceparent` header; OTel publishes `traceId`/`spanId`
   MDC automatically. Your log lines (anywhere in the consuming thread) now carry the trace.
3. On **Kafka outbound**: `spring.kafka.template.observation-enabled=true` makes the
   `KafkaTemplate` inject a fresh `traceparent` header derived from the *current* span.

> **Consequence:** as long as the §2 deps + §3 properties are present, `%X{traceId}` and
> `%X{spanId}` in logback "just work" for HTTP and Kafka. There is nothing to code for tracing.

**The only thing you must code manually is `X-Correlation-Id`** — because correlation is a
business concept OTel does not know natively. That is where the filter and interceptor come in.

---

## 6. Inject `X-Correlation-Id` → MDC **and** baggage — and why

We set the correlation id into **both** the SLF4J MDC **and** Micrometer baggage.

- **MDC** (via `MDC.put`) → appears in log lines and the Loki `correlationId` field, so you can
  grep/filter logs by business flow.
- **Baggage** (via `BaggageManager`) → propagates `X-Correlation-Id` across the process boundary
  (HTTP header / Kafka header) **and** surfaces it as a span tag (enabled by the
  `management.tracing.baggage.tag-fields=X-Correlation-Id` property), so it is searchable in
  Jaeger by tag.

If you only put it in MDC, it is local to the process and lost when the context hops services.
If you only use baggage, it is not in the log lines. **Do both.**

---

## 7. HTTP inbound — the correlation filter

Add one `OncePerRequestFilter` that (a) reuses a caller-provided `X-Correlation-Id` or generates
one, (b) writes it to MDC, (c) creates a baggage scope, and (d) echoes it back on the response
so callers can correlate their own logging. Use `@Order(Ordered.HIGHEST_PRECEDENCE)` so it runs
before business filters.

```java
package com.your.service.filters;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component()
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID = "X-Correlation-Id";

    private final BaggageManager baggageManager;

    public CorrelationIdFilter(BaggageManager baggageManager) {
        this.baggageManager = baggageManager;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CORRELATION_ID, correlationId);

        try (BaggageInScope ignored = baggageManager.createBaggageInScope(CORRELATION_ID, correlationId)) {
            response.addHeader(CORRELATION_ID, correlationId);
            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC.remove(CORRELATION_ID);
            }
        }
    }
}
```

Put the constant in your message-headers class (`MessageHeaders.CORRELATION_ID`) instead of
hardcoding it, as payment does.

> **Why echo it back?** So a downstream caller can reuse the same id on subsequent requests —
> enabling end-to-end correlation across distributed HTTP calling chains.

---

## 8. Kafka inbound — the correlation interceptor

Even though `traceparent` is auto-managed by OTel, the **`X-Correlation-Id` header is not**. Wire
a Spring Kafka `RecordInterceptor` so every consumed record populates MDC + baggage for the
duration of the `@KafkaListener`, then cleans up.

```java
package com.your.service.messaging.config;

import io.micrometer.tracing.BaggageManager;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class KafkaCorrelationIdInterceptor implements RecordInterceptor<Object, Object> {

    public static final String CORRELATION_ID = "X-Correlation-Id";

    private final BaggageManager baggageManager;

    public KafkaCorrelationIdInterceptor(BaggageManager baggageManager) {
        this.baggageManager = baggageManager;
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        String correlationId = extractHeader(record, CORRELATION_ID);
        if (correlationId != null) {
            MDC.put(CORRELATION_ID, correlationId);
            baggageManager.createBaggageInScope(CORRELATION_ID, correlationId);
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(CORRELATION_ID);
    }

    private String extractHeader(ConsumerRecord<Object, Object> record, String key) {
        var header = record.headers().lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
```

**Register it either:**

- **(a) Auto-configured factory** — it is a `@Component` `RecordInterceptor`, Spring Boot picks
  it up automatically when you use the default `ConcurrentKafkaListenerContainerFactory`. ✅ Simple.
- **(b) Custom factory** — if (like `rally-notification`) you define your **own**
  `ConcurrentKafkaListenerContainerFactory` bean, wire it explicitly:

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
        ConsumerFactory<String, Object> consumerFactory, KafkaCorrelationIdInterceptor interceptor) {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setRecordInterceptor(interceptor);
    // ... ack mode, error handler ...
    return factory;
}
```

> **Warning:** if you define your own factory, the auto-configured one is not used — so the
> interceptor is NOT wired unless you add it yourself. Always check which factory your
> `@KafkaListener`s actually use.

---

## 9. Propagate correlation + trace into the OUTBOX message

When your service writes an outbox row (on the same thread as the request/consumer), bake both
values **into the `headers` JSON column** and store the `traceId` on the row. This is what lets
the relay (next section) reconstruct the original trace later.

```java
// Example: mirror rally-payment OutboxEventWriter / PaymentEventPublisher
String traceId = MDC.get("traceId");            // populated by OTel (auto)
String correlationId = MDC.get("X-Correlation-Id"); // populated by your filter/interceptor

ObjectNode headers = OBJECT_MAPPER.createObjectNode();
headers.put("X-Id", messageId.toString());
headers.put("X-Type", eventType);
headers.put("X-Correlation-Id", correlationId);

OutboxMessage message = OutboxMessage.builder()
        .messageId(messageId)
        .topic(TOPIC)
        .messageKey(key)
        .messageType(eventType)
        .correlationId(UUID.fromString(correlationId))
        .traceId(traceId)             // <- persisted on the ROW for the relay
        .payload(payload)
        .headers(headers)             // <- correlation (+ optional traceparent) for downstream
        .build();
outboxRepository.save(message);
```

**Storing `traceId` on the row is the crucial bit.** The relay will use it to "re-parent" its
publish span back onto the original trace.

> No manual `traceparent` header *must* be written here in modern setups: when the relay
> re-parents (next section) and uses a tracing-enabled `KafkaTemplate`, the `traceparent`
> header is auto-derived. Some services also put `traceparent` into `headers` explicitly — both
> approaches are fine; the persisted `traceId` on the row is the source of truth for the relay.

---

## 10. RE-PARENT the span while publishing through the RELAY

The outbox row is written inside the business transaction (trace `A`), but the relay runs later
on a **scheduled thread with no active trace**. If it just publishes, the publish span becomes a
brand-new trace (`B`) and is disconnected. To keep it under the original trace (`A`), **re-parent**
the publish span to the stored `traceId`.

Implement `reParentToStoredTrace()` + wire it into the relay loop. This is exactly the
`rally-payment` `OutboxRelay` pattern:

```java
public static final Pattern OTLP_TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");

// per outbox message, in relayPending():
for (OutboxMessage message : pending) {
    Span span = reParentToStoredTrace(message);
    try (Tracer.SpanInScope ignored = span != null ? tracer.withSpan(span) : null) {
        boolean ok = outboxPublisher.publish(message);
        // ... success/failure handling, status transitions, logging ...
    } finally {
        if (span != null) {
            span.end();
        }
    }
    outboxRepository.save(message);
}

private Span reParentToStoredTrace(OutboxMessage message) {
    String traceId = message.getTraceId();
    if (traceId == null || !OTLP_TRACE_ID_PATTERN.matcher(traceId).matches()) {
        return null;
    }
    return tracer.spanBuilder()
            .name("relay-outbox-message")
            .setParent(tracer.traceContextBuilder()
                    .traceId(traceId)
                    .spanId(randomValidSpanId())
                    .sampled(true)
                    .build())
            .start();
}

public static String randomValidSpanId() {
    return String.format("%016x", ThreadLocalRandom.current().nextLong());
}
```

> **Why this matters:** without re-parenting, the published Kafka message gets a fresh
> `traceparent` (trace `B`) and every downstream consumer/log is on `B`, severing the link to the
> business transaction that created it. With it, the publish (and thus downstream consumers)
> inherit **trace `A`** — one continuous trace.

---

## 11. Step-by-step checklist for a new service

1. **pom.xml** — add the 6 dependencies from §2.
2. **application.properties** — add the observability block §3 + `loki.url` + ensure
   `spring.kafka.*.observation-enabled=true`.
3. **logback-spring.xml** — add with your `app = <service>` label (§4).
4. **HTTP inbound** (if you have REST) — add `CorrelationIdFilter` (§7).
5. **Kafka inbound** (if you consume) — add `KafkaCorrelationIdInterceptor` and, if you use a
   custom factory, wire it via `setRecordInterceptor(...)` (§8).
6. **Outbox writer** — persist `traceId` on the row + `X-Correlation-Id` in headers (§9).
7. **Outbox relay** — add `reParentToStoredTrace(...)` + `Tracer` (§10).
8. Build, run, and verify (below).

> **Kafka-only service (like `rally-notification`):** skip §7 (no HTTP). If it has *no* producer,
> also skip §9/§10 — just do §8 to correlate the inbound message and its logs.
>
> **HTTP-only producer (like `rally-auth`):** skip §8 (no consumer); do §7 + §9 + §10.

---

## 12. Verify it works

**Jaeger** (traces):
- Produce/consume a message and open its trace.
- Confirm the outbox relay publish span and all downstream consumer spans share the **same
  `traceId`** as the initiating HTTP/Kafka span.
- Filter by the `X-Correlation-Id` span tag.

**Loki** (logs):
```logql
{app="your-service"} |= "<traceId>"
{app="your-service"} |= "<X-Correlation-Id>"
```
- Every log line should carry the same `traceId` (`%X{traceId}`) and `correlationId`
  (`%X{X-Correlation-Id}`).

If a downstream service's logs show a different `traceId`, it means the producer relay is not
re-parenting (check §10) or the consumer isn't restoring the inbound `traceparent` (check §3/§8).