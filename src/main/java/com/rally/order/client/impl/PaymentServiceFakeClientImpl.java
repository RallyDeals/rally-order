package com.rally.order.client.impl;

import com.rally.order.client.PaymentServiceClient;
import com.rally.order.client.dto.PaymentMethodDetails;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Profile("dev")
@Component
public class PaymentServiceFakeClientImpl implements PaymentServiceClient {

    @Override
    public PaymentMethodDetails getPaymentMethodDetails(UUID userId, String paymentMethodId) {
        return PaymentMethodDetails.builder()
                .cardLast4("4242")
                .cardBrand("visa")
                .cardExpMonth("12")
                .cardExpYear("2030")
                .build();
    }
}
