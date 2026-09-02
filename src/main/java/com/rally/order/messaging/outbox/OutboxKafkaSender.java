package com.rally.order.messaging.outbox;

import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.support.EventHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxKafkaSender {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public CompletableFuture<Void> send(OutboxEvent event){
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(), event.getAggregateId().toString(), event.getPayload());

        EventHeaders headers = new EventHeaders(event.getId(), event.getEventType(), event.getCorrelationId());
        record.headers()
                .add(new RecordHeader(KafkaTopics.HEADER_EVENT_ID, headers.eventId().toString().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader(KafkaTopics.HEADER_EVENT_TYPE, headers.eventType().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader(KafkaTopics.HEADER_CORRELATION_ID, headers.correlationId().toString().getBytes(StandardCharsets.UTF_8)));

        return kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish event {} (type={}) to topic {}",
                                headers.eventId(), headers.eventType(), event.getTopic(), ex);
                    } else {
                        log.info("Published event {} (type={}) to topic {} partition {} offset {}",
                                headers.eventId(), headers.eventType(), event.getTopic(),
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                })
                .thenApply(result -> null);
    }
}
