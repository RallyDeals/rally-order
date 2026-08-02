package com.rally.order.messaging.processed;

import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.consumer.TopicConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProcessedEventsService {
    private final ProcessedEventsRepository processedEventsRepository;
    private final Map<String, TopicConsumer> topicConsumers;

    public ProcessedEventsService(ProcessedEventsRepository processedEventsRepository,
                                  List<TopicConsumer> consumers) {
        this.processedEventsRepository = processedEventsRepository;
        this.topicConsumers = consumers.stream()
                .collect(Collectors.toMap(TopicConsumer::getTopic, Function.identity()));
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT, groupId = "order-payment-group")
    @Transactional
    public void onPaymentMessage(ConsumerRecord<String, Object> record) {
        handle(record);
    }

    public void handle(ConsumerRecord<String, Object> record) {
        String eventId = new String(record.headers().lastHeader(KafkaTopics.HEADER_EVENT_ID).value(), StandardCharsets.UTF_8);
        if (processedEventsRepository.existsById(eventId)){
            log.info("Event with ID {} has already been processed. Skipping.", eventId);
            return;
        }
        String eventType = new String(record.headers().lastHeader(KafkaTopics.HEADER_EVENT_TYPE).value(), StandardCharsets.UTF_8);
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .sourceTopic(record.topic())
                .processedAt(java.time.OffsetDateTime.now())
                .build();

        TopicConsumer consumer = topicConsumers.get(record.topic());
        if(consumer == null)
            throw new IllegalStateException("No consumer registered for topic: " + record.topic());

        processedEventsRepository.save(processedEvent);
        consumer.onMessage(record);
    }
}
