package com.rally.order.messaging.processed;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventsRepository extends JpaRepository<ProcessedEvent, String> {
    @Override
    boolean existsById(@NonNull String id);
}
