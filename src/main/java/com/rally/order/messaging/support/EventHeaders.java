package com.rally.order.messaging.support;

import java.util.UUID;

public record EventHeaders(UUID eventId, String eventType, UUID correlationId) {
}
