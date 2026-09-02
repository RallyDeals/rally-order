package com.rally.order.messaging.consumer;

import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.event.inbound.participation.ParticipantJoined;
import com.rally.order.messaging.event.inbound.participation.ParticipantLeft;
import com.rally.order.messaging.support.EventTypes;
import com.rally.order.service.DealOrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ParticipantTopicConsumerTest {

    @Mock
    private DealOrderService dealOrderService;

    @InjectMocks
    private ParticipantTopicConsumer consumer;

    @Test
    void getTopic_returnsParticipationTopic() {
        assertEquals(KafkaTopics.PARTICIPATION, consumer.getTopic());
    }

    @Test
    void onMessage_participantJoined_dispatchesToHandleParticipationJoin() {
        ParticipantJoined payload = new ParticipantJoined(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "pm_123", "123 Main St", Instant.parse("2026-08-22T10:15:30Z"));
        ConsumerRecord<String, Object> record = record(EventTypes.PARTICIPANT_JOINED, payload);

        consumer.onMessage(record);

        verify(dealOrderService).handleParticipationJoin(payload);
    }

    @Test
    void onMessage_participantLeft_dispatchesToHandleParticipationLeft() {
        ParticipantLeft payload = new ParticipantLeft(UUID.randomUUID(), UUID.randomUUID());
        ConsumerRecord<String, Object> record = record(EventTypes.PARTICIPANT_LEFT, payload);

        consumer.onMessage(record);

        verify(dealOrderService).handleParticipationLeft(payload);
    }

    @Test
    void onMessage_missingTypeHeader_doesNothing() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(KafkaTopics.PARTICIPATION, 0, 0L, "key",
                new ParticipantLeft(UUID.randomUUID(), UUID.randomUUID()));

        consumer.onMessage(record);

        verifyNoInteractions(dealOrderService);
    }

    @Test
    void onMessage_unknownEventType_doesNothing() {
        ConsumerRecord<String, Object> record = record("Participant.SomethingElse", new ParticipantLeft(UUID.randomUUID(), UUID.randomUUID()));

        consumer.onMessage(record);

        verifyNoInteractions(dealOrderService);
    }

    private ConsumerRecord<String, Object> record(String eventType, Object payload) {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(KafkaTopics.PARTICIPATION, 0, 0L, "key", payload);
        record.headers().add(new RecordHeader(KafkaTopics.HEADER_EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8)));
        return record;
    }
}
