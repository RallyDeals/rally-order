package com.rally.order.client;

import com.rally.order.client.dto.PaymentMethodDetails;

import java.util.UUID;

public interface PaymentServiceClient {
    PaymentMethodDetails getPaymentMethodDetails(UUID userId, String paymentMethodId);
}
