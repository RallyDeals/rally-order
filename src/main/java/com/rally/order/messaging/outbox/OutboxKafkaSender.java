package com.rally.order.messaging.outbox;

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
                event.getTopic(), 1, event.getAggregateId(), event.getPayload());

        record.headers()
                .add(new RecordHeader("X-Id", event.getId().toString().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader("X-Type", event.getEventType().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader("X-Correlation-Id", event.getCorrelationId().getBytes(StandardCharsets.UTF_8)));

        return kafkaTemplate.send(record).thenApply(result -> null);
    }
}
