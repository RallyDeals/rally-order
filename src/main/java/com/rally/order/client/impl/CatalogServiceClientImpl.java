package com.rally.order.client.impl;

import com.rally.common.exceptions.shared.InternalServerErrorException;
import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.CatalogServiceClient;
import com.rally.order.client.dto.CatalogLookupRequest;
import com.rally.order.client.dto.CatalogLookupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Profile("prod")
@Component
@RequiredArgsConstructor
public class CatalogServiceClientImpl implements CatalogServiceClient {
    private final RestTemplate restTemplate;

    @Value("${catalog.service.url}")
    private String catalogServiceUrl;

    @Override
    public CatalogLookupResponse lookup(CatalogLookupRequest request) {
        String url = catalogServiceUrl + "/products/lookup";

        try{
            ResponseEntity<CatalogLookupResponse> response =
                    restTemplate.postForEntity(url, request, CatalogLookupResponse.class);
            return response.getBody();
        }catch (ResourceAccessException resourceAccessException){
            throw new ServiceUnavailableException("Catalog service is unavailable");
        } catch(HttpServerErrorException serverErrorException){
            throw new InternalServerErrorException("Catalog service returned server error");
        }
    }
}
