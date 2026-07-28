package com.rally.order.messaging.outbox;

import com.rally.order.messaging.support.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;


    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, UUID aggregateId, String eventType, String topic, Object eventRecord){
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        String payload = objectMapper.writeValueAsString(eventRecord);

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(topic)
                .payload(payload)
                .correlationId(TraceContext.correlationId())
                .causationId(TraceContext.causationId())
                .traceId(TraceContext.traceId())
                .status(OutboxEventStatus.PENDING)
                .attempts(0)
                .createdAt(java.time.OffsetDateTime.now())
                .build();

        outboxEventRepository.save(event);
    }
}
