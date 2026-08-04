package com.rally.order.messaging.event.outbound.orderEvents;

import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderAuthorized(UUID dealId, UUID userId) {
}
