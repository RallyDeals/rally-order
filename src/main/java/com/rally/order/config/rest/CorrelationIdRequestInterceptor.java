package com.rally.order.config.rest;

import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.support.TraceContext;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CorrelationIdRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(KafkaTopics.HEADER_CORRELATION_ID, TraceContext.correlationId().toString());
        return execution.execute(request, body);
    }
}
