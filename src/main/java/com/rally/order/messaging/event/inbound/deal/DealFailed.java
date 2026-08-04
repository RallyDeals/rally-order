package com.rally.order.messaging.event.inbound.deal;

import java.util.UUID;

public record DealFailed(UUID dealId, int reservedStock, int authorizedCount) {
}
