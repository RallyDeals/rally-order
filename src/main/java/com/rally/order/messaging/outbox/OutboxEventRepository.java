package com.rally.order.messaging.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT * FROM outbox_events WHERE status = :status " +
            "ORDER BY created_at LIMIT :limit", nativeQuery = true)
    List<OutboxEvent> lockNextBatch(String status, int limit);
}
