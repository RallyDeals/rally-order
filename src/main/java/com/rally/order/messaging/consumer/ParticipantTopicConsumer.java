package com.rally.order.messaging.consumer;

import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.event.inbound.participation.ParticipantJoined;
import com.rally.order.messaging.event.inbound.participation.ParticipantLeft;
import com.rally.order.messaging.support.EventTypes;
import com.rally.order.service.DealOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class ParticipantTopicConsumer implements TopicConsumer{
    private final DealOrderService dealOrderService;

    @Override
    public String getTopic() {
        return KafkaTopics.PARTICIPATION;
    }

    @Override
    public void onMessage(ConsumerRecord<String, Object> record){
        String eventType = extractType(record);
        if (eventType == null)
            return;
        switch(eventType){
            case EventTypes.PARTICIPANT_JOINED ->
                    dealOrderService.handleParticipationJoin((ParticipantJoined) record.value());
            case EventTypes.PARTICIPANT_LEFT ->
                    dealOrderService.handleParticipationLeft((ParticipantLeft) record.value());
            default -> System.out.println("Unhandled participation event type: " + eventType);
        }

    }
    private String extractType(ConsumerRecord<String, Object> record) {
        Header header = record.headers().lastHeader(KafkaTopics.HEADER_EVENT_TYPE);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
