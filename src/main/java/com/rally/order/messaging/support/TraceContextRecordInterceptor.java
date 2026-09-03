package com.rally.order.messaging.support;

import com.rally.order.support.TraceContext;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

import static com.rally.order.messaging.config.KafkaTopics.HEADER_CORRELATION_ID;

/**
 * Applies the correlation id to each Kafka consumer record.
 * It is triggered when a message is received before the listener logic runs,
 * so the same trace id can be attached to the current thread for logging and
 * downstream processing. After the record is handled, it clears the MDC state
 * to prevent one message's context from leaking into another.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceContextRecordInterceptor implements RecordInterceptor<String, Object> {

    private final BaggageManager baggageManager;
    private final ThreadLocal<BaggageInScope> baggageScope = new ThreadLocal<>();

    @Override
    public ConsumerRecord<String, Object> intercept(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        Supplier<UUID> fallback = UUID::randomUUID;
        UUID correlationId = headerUuid(record, HEADER_CORRELATION_ID, fallback);

        TraceContext.put(correlationId);
        baggageScope.set(baggageManager.createBaggageInScope(HEADER_CORRELATION_ID, correlationId.toString()));
        log.info("Consumed record from topic {} partition {} offset {} key {}",
                record.topic(), record.partition(), record.offset(), record.key());
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        TraceContext.clear();
        BaggageInScope scope = baggageScope.get();
        if (scope != null) {
            scope.close();
            baggageScope.remove();
        }
    }

    private UUID headerUuid(ConsumerRecord<String, Object> record, String key, Supplier<UUID> fallback) {
        Header header = record.headers().lastHeader(key);
        if (header == null) {
            return fallback.get();
        }
        try {
            return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return fallback.get();
        }
    }
}