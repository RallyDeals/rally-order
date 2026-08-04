package com.rally.order.client.impl;

import com.rally.order.client.DealServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Profile("dev")
@Component
public class DealServiceFakeClientImpl implements DealServiceClient {

    @Value("${deal.service.fake-status-code:200}")
    private int fakeStatusCode;

    @Override
    public boolean authorizeSlot(UUID dealId) {
        return HttpStatus.valueOf(fakeStatusCode).is2xxSuccessful();
    }

    @Override
    public void releaseSlot(UUID dealId) {
    }

    @Override
    public void releaseAuthorizedSlot(UUID dealId) {
    }
}
