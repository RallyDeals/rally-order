package com.rally.order.messaging.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter @Setter
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(length = 50, nullable = false)
    private String aggregateType;
    @Column(length = 100, nullable = false)
    private String aggregateId;
    @Column(length = 100, nullable = false)
    private String eventType;
    @Column(length = 100, nullable = false)
    private String topic;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;
    @Column(nullable = false)
    private int attempts = 0;
    private String lastError;
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
