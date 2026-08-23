package com.rally.order.client;

import java.util.UUID;

public interface DealServiceClient {
    boolean authorizeSlot(UUID dealId, UUID orderId);
    void releaseSlot(UUID dealId, UUID orderId);
    void releaseAuthorizedSlot(UUID dealId, UUID orderId);
}
