package com.rally.order.client.impl;

import com.rally.common.exceptions.shared.InternalServerErrorException;
import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.PaymentServiceClient;
import com.rally.order.client.dto.PaymentMethodDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Profile("prod")
@Component
@RequiredArgsConstructor
public class PaymentServiceClientImpl implements PaymentServiceClient {
    private final RestTemplate restTemplate;

    @Value("${payment.service.url}")
    private String paymentServiceUrl;

    @Override
    public PaymentMethodDetails getPaymentMethodDetails(UUID userId, String paymentMethodId) {
        String url = paymentServiceUrl + "/api" + "/payment-methods/" + paymentMethodId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<PaymentMethodDetails> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    PaymentMethodDetails.class
            );
            return response.getBody();
        } catch (ResourceAccessException resourceAccessException) {
            log.warn("Payment service unavailable calling {}", url, resourceAccessException);
            throw new ServiceUnavailableException("Payment service is unavailable");
        } catch (HttpServerErrorException serverErrorException) {
            log.warn("Payment service returned server error calling {}", url, serverErrorException);
            throw new InternalServerErrorException("Payment service returned server error");
        }
    }
}
