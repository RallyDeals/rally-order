package com.rally.order.messaging.event.inbound.deal;

import java.util.UUID;

public record DealSucceeded(UUID dealId, int dealStock, int authorizedCount) {
}
