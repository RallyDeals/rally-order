package com.rally.order.messaging.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class OutboxRelay {
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxKafkaSender outboxKafkaSender;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relay(){
        List<OutboxEvent> events = outboxEventRepository.lockNextBatch(OutboxEventStatus.PENDING.name(), BATCH_SIZE);
        for(OutboxEvent event: events){
            try{
                outboxKafkaSender.send(event).get();
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setPublishedAt(OffsetDateTime.now());
            }catch (ExecutionException | InterruptedException e){
                if(e instanceof InterruptedException){
                    Thread.currentThread().interrupt();
                }
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(rootCauseError(e));
                event.setStatus(attempts >= MAX_ATTEMPTS ? OutboxEventStatus.FAILED : OutboxEventStatus.PENDING);
            }
        }
    }

    private String rootCauseError(Throwable t){
        Throwable cause = t.getCause() != null? t.getCause(): t;
        String msg = cause.getMessage();
        return msg != null && msg.length() > 500? msg.substring(0, 500): msg;
    }

}
