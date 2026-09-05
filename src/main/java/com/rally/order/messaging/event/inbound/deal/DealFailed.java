package com.rally.order.messaging.event.inbound.deal;

import java.time.Instant;
import java.util.UUID;

public record DealFailed(Instant occurredAt, UUID dealId, Integer reservedStock, Integer authorizedCount, UUID productId, Integer quantity) {
}
