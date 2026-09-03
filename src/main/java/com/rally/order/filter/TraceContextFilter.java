package com.rally.order.filter;

import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.support.TraceContext;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;


/**
 * Initializes the correlation id for every incoming HTTP request.
 * It runs once per request before the controller/filter chain is executed,
 * and it is triggered on every REST call entering the application.
 * Its job is to create a new UUID when the client does not send one,
 * store it in MDC/TraceContext, expose it on the response header, and
 * clear it again after the request completes so logs and downstream logic
 * share the same trace identity for that request only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceContextFilter extends OncePerRequestFilter {

    private final BaggageManager baggageManager;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        UUID correlationId = uuidHeader(request, KafkaTopics.HEADER_CORRELATION_ID);

        TraceContext.put(correlationId);
        response.setHeader(KafkaTopics.HEADER_CORRELATION_ID, correlationId.toString());
        long startNanos = System.nanoTime();
        try (BaggageInScope ignored = baggageManager.createBaggageInScope(
                KafkaTopics.HEADER_CORRELATION_ID, correlationId.toString())) {
            filterChain.doFilter(request, response);
        } finally {
            log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), (System.nanoTime() - startNanos) / 1_000_000);
            TraceContext.clear();
        }
    }

    private UUID uuidHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
    }
}