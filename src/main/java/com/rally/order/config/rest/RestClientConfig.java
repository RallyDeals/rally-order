package com.rally.order.config.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class RestClientConfig {
    private final CorrelationIdRequestInterceptor correlationIdRequestInterceptor;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder){
        return restTemplateBuilder
                .connectTimeout(Duration.ofMillis(2000))
                .readTimeout(Duration.ofMillis(5000))
                .additionalInterceptors(correlationIdRequestInterceptor)
                .build();
    }
}
