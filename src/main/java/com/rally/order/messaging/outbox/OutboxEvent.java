package com.rally.order.messaging.outbox;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @UuidGenerator
    private UUID id;
    @Column(length = 50, nullable = false)
    private String aggregateType;
    @Column(nullable = false)
    private UUID aggregateId;
    @Column(length = 100, nullable = false)
    private String eventType;
    @Column(length = 100, nullable = false)
    private String topic;
    @Column(nullable = false)
    private UUID correlationId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private OutboxEventStatus status = OutboxEventStatus.PENDING;
    @Column(nullable = false)
    private int attempts = 0;
    private String lastError;
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
