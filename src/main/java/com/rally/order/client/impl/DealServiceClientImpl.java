package com.rally.order.client.impl;


import com.rally.common.exceptions.shared.InternalServerErrorException;
import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.DealServiceClient;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Profile("prod")
@Component
@RequiredArgsConstructor
public class DealServiceClientImpl implements DealServiceClient {
    private final RestTemplate restTemplate;

    @Value("${deal.service.url}")
    private String dealServiceUrl;

    @Override
    public boolean authorizeSlot(UUID dealId, UUID orderId) {
        String url = dealServiceUrl + "/internal/deals/" + dealId + "/authorize-slot";
        HttpEntity<Void> requestEntity = requestEntity("authorize-slot", orderId);

        try{
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (ResourceAccessException resourceAccessException) {
            log.warn("Deal service unavailable calling {}", url, resourceAccessException);
            throw new ServiceUnavailableException("Deal service is unavailable");
        } catch(HttpServerErrorException serverErrorException){
            log.warn("Deal service returned server error calling {}", url, serverErrorException);
            throw new InternalServerErrorException("Deal service returned server error");
        }
    }

    @Override
    public void releaseSlot(UUID dealId, UUID orderId) {
        String url = dealServiceUrl + "/internal/deals/" + dealId + "/release-slot";
        HttpEntity<Void> requestEntity = requestEntity("release-slot", orderId);

        try{
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, Void.class);
        } catch (ResourceAccessException resourceAccessException) {
            log.warn("Deal service unavailable calling {}", url, resourceAccessException);
            throw new ServiceUnavailableException("Deal service is unavailable");
        } catch(HttpServerErrorException serverErrorException){
            log.warn("Deal service returned server error calling {}", url, serverErrorException);
            throw new InternalServerErrorException("Deal service returned server error");
        }
    }

    @Override
    public void releaseAuthorizedSlot(UUID dealId, UUID orderId) {
        String url = dealServiceUrl + "/internal/deals/" + dealId + "/release-authorized-slot";
        HttpEntity<Void> requestEntity = requestEntity("release-authorized-slot", orderId);

        try{
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, Void.class);
        } catch (ResourceAccessException resourceAccessException) {
            log.warn("Deal service unavailable calling {}", url, resourceAccessException);
            throw new ServiceUnavailableException("Deal service is unavailable");
        } catch(HttpServerErrorException serverErrorException){
            log.warn("Deal service returned server error calling {}", url, serverErrorException);
            throw new InternalServerErrorException("Deal service returned server error");
        }
    }

    private HttpEntity<Void> requestEntity(String operation, UUID orderId) {
        UUID requestId = UUID.nameUUIDFromBytes((operation + ":" + orderId).getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("requestId", requestId.toString());
        return new HttpEntity<>(headers);
    }
}