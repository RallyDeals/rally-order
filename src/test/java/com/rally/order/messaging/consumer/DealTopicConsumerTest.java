package com.rally.order.messaging.consumer;

import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.event.inbound.deal.DealFailed;
import com.rally.order.messaging.event.inbound.deal.DealSucceeded;
import com.rally.order.messaging.support.EventTypes;
import com.rally.order.service.DealOrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DealTopicConsumerTest {

    @Mock
    private DealOrderService dealOrderService;

    @InjectMocks
    private DealTopicConsumer consumer;

    @Test
    void getTopic_returnsDealTopic() {
        assertEquals(KafkaTopics.DEAL, consumer.getTopic());
    }

    @Test
    void onMessage_dealSucceeded_dispatchesToHandleDealSucceeded() {
        DealSucceeded payload = new DealSucceeded(UUID.randomUUID(), 10, 5);
        ConsumerRecord<String, Object> record = record(EventTypes.DEAL_SUCCEEDED, payload);

        consumer.onMessage(record);

        verify(dealOrderService).handleDealSucceeded(payload);
    }

    @Test
    void onMessage_dealFailed_dispatchesToHandleDealFailed() {
        DealFailed payload = new DealFailed(UUID.randomUUID(), 10, 5);
        ConsumerRecord<String, Object> record = record(EventTypes.DEAL_FAILED, payload);

        consumer.onMessage(record);

        verify(dealOrderService).handleDealFailed(payload);
    }

    @Test
    void onMessage_missingTypeHeader_doesNothing() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(KafkaTopics.DEAL, 0, 0L, "key", new DealSucceeded(UUID.randomUUID(), 1, 1));

        consumer.onMessage(record);

        verifyNoInteractions(dealOrderService);
    }

    @Test
    void onMessage_unknownEventType_doesNothing() {
        ConsumerRecord<String, Object> record = record("Deal.SomethingElse", new DealSucceeded(UUID.randomUUID(), 1, 1));

        consumer.onMessage(record);

        verifyNoInteractions(dealOrderService);
    }

    private ConsumerRecord<String, Object> record(String eventType, Object payload) {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(KafkaTopics.DEAL, 0, 0L, "key", payload);
        record.headers().add(new RecordHeader(KafkaTopics.HEADER_EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8)));
        return record;
    }
}
