package com.rally.order.messaging.event.inbound.participation;

import java.util.UUID;

public record ParticipantLeft(UUID participantId, UUID dealId) {
}
