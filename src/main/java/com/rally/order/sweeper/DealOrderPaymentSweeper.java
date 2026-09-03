package com.rally.order.sweeper;

import com.rally.order.support.TraceContext;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.repository.OrderRepository;
import com.rally.order.service.DealOrderService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DealOrderPaymentSweeper {
    private final OrderRepository orderRepository;
    private final DealOrderService dealOrderService;
    private final Tracer tracer;

    @Value("${sweeper.deal-order.payment.stale-after}")
    private long paymentStaleAfterSeconds;

    @Value("${sweeper.deal-order.payment.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${sweeper.deal-order.payment.fixed-delay-ms}")
    @Transactional
    public void sweepStalePayments() {
        Instant threshold = Instant.now().minusSeconds(paymentStaleAfterSeconds);
        List<Order> staleOrders = orderRepository.lockStaleOrders(OrderStatus.PENDING_AUTHORIZATION.name(), threshold, batchSize);
        for (Order order : staleOrders) {
            log.info("Expiring order {} stuck in PENDING_AUTHORIZATION since {}", order.getId(), order.getStatusUpdatedAt());
            UUID correlationId = UUID.randomUUID();
            TraceContext.put(correlationId);
            Span span = tracer.nextSpan().name("sweeper.deal-order.payment").start();
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                dealOrderService.cancelStuckPendingAuthorizationOrder(order);
            } finally {
                span.end();
                TraceContext.clear();
            }
        }
    }
}