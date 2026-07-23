package com.rally.order.messaging.processed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;

@Entity
@Getter @Setter
@Table(name = "processed_events")
public class ProcessedEvent {
    @Id
    @UuidGenerator
    @Column(length = 100)
    private String eventId;
    @Column(length = 100, nullable = false)
    private String eventType;
    @Column(length = 100, nullable = false)
    private String sourceTopic;
    @Column(nullable = false)
    private OffsetDateTime processedAt;
}
