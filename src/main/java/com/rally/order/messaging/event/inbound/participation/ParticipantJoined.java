package com.rally.order.messaging.event.inbound.participation;

import java.math.BigDecimal;
import java.util.UUID;

record ParticipantJoined(UUID participantId, UUID dealId, UUID userId, UUID productId,
                         BigDecimal price, String paymentIntentId) {
}
