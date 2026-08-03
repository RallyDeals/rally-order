package com.rally.order.client;

import java.util.UUID;

public interface DealServiceClient {
    boolean authorizeSlot(UUID dealId);
    void releaseSlot(UUID dealId);
    void releaseAuthorizedSlot(UUID dealId);
}
