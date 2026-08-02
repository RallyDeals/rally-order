package com.rally.order.messaging.event.outbound.orderEvents;

import java.util.UUID;

public record OrderAuthorized(UUID dealId, UUID participantId) {
}
