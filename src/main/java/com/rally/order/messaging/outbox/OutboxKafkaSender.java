package com.rally.order.messaging.outbox;

import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.support.EventHeaders;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class OutboxKafkaSender {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public CompletableFuture<Void> send(OutboxEvent event){
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(), event.getAggregateId().toString(), event.getPayload());

        EventHeaders headers = new EventHeaders(event.getId(), event.getEventType(), event.getCorrelationId(), event.getCausationId(), event.getTraceId());
        record.headers()
                .add(new RecordHeader(KafkaTopics.HEADER_EVENT_ID, headers.eventId().toString().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader(KafkaTopics.HEADER_EVENT_TYPE, headers.eventType().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader(KafkaTopics.HEADER_CORRELATION_ID, headers.correlationId().toString().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader(KafkaTopics.HEADER_CAUSATION_ID, headers.causationId().toString().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader(KafkaTopics.HEADER_TRACE_ID, headers.traceId().toString().getBytes(StandardCharsets.UTF_8)));

        return kafkaTemplate.send(record).thenApply(result -> null);
    }
}
