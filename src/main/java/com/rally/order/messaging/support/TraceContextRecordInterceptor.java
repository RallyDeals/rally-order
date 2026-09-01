package com.rally.order.messaging.support;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

import static com.rally.order.messaging.config.KafkaTopics.HEADER_CORRELATION_ID;

@Component
public class TraceContextRecordInterceptor implements RecordInterceptor<String, Object> {

    @Override
    public ConsumerRecord<String, Object> intercept(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        Supplier<UUID> fallback = UUID::randomUUID;
        UUID correlationId = headerUuid(record, HEADER_CORRELATION_ID, fallback);

        TraceContext.put(correlationId);
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        TraceContext.clear();
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