package com.rally.order.client.impl;

import com.rally.common.exceptions.shared.InternalServerErrorException;
import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.PaymentServiceClient;
import com.rally.order.client.dto.PaymentMethodDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Profile("prod")
@Component
@RequiredArgsConstructor
public class PaymentServiceClientImpl implements PaymentServiceClient {
    private final RestTemplate restTemplate;

    @Value("${payment.service.url}")
    private String paymentServiceUrl;

    @Override
    public PaymentMethodDetails getPaymentMethodDetails(UUID userId, String paymentMethodId) {
        String url = paymentServiceUrl + "api/users/" + userId + "/payment-methods/" + paymentMethodId;

        try {
            ResponseEntity<PaymentMethodDetails> response =
                    restTemplate.getForEntity(url, PaymentMethodDetails.class);
            return response.getBody();
        } catch (ResourceAccessException resourceAccessException) {
            throw new ServiceUnavailableException("Payment service is unavailable");
        } catch (HttpServerErrorException serverErrorException) {
            throw new InternalServerErrorException("Payment service returned server error");
        }
    }
}
