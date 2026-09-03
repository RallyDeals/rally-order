package com.rally.order.messaging.outbox;

import com.rally.order.support.TraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern OTLP_TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxKafkaSender outboxKafkaSender;
    private final Tracer tracer;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relay(){
        List<OutboxEvent> events = outboxEventRepository.lockNextBatch(OutboxEventStatus.PENDING.name(), BATCH_SIZE);
        for(OutboxEvent event: events){
            TraceContext.put(event.getCorrelationId());
            Span span = reParentToStoredTrace(event);
            try (Tracer.SpanInScope ignored = span != null ? tracer.withSpan(span) : null) {
                outboxKafkaSender.send(event).get();
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setPublishedAt(OffsetDateTime.now());
                log.debug("Published outbox event {} ({}) to {}", event.getId(), event.getEventType(), event.getTopic());
            }catch (ExecutionException | InterruptedException e){
                if(e instanceof InterruptedException){
                    Thread.currentThread().interrupt();
                }
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(rootCauseError(e));
                event.setStatus(attempts >= MAX_ATTEMPTS ? OutboxEventStatus.FAILED : OutboxEventStatus.PENDING);
                if (attempts >= MAX_ATTEMPTS) {
                    log.error("Outbox event {} ({}) failed permanently after {} attempts", event.getId(), event.getEventType(), attempts, e);
                } else {
                    log.warn("Outbox event {} ({}) failed on attempt {}, will retry", event.getId(), event.getEventType(), attempts, e);
                }
            } finally {
                if (span != null) {
                    span.end();
                }
                TraceContext.clear();
            }
        }
    }

    private Span reParentToStoredTrace(OutboxEvent event) {
        String traceId = event.getTraceId();
        if (traceId == null || !OTLP_TRACE_ID_PATTERN.matcher(traceId).matches()) {
            return null;
        }
        return tracer.spanBuilder()
                .name("relay-outbox-event")
                .setParent(tracer.traceContextBuilder().traceId(traceId).build())
                .start();
    }

    private String rootCauseError(Throwable t){
        Throwable cause = t.getCause() != null? t.getCause(): t;
        String msg = cause.getMessage();
        return msg != null && msg.length() > 500? msg.substring(0, 500): msg;
    }

}
