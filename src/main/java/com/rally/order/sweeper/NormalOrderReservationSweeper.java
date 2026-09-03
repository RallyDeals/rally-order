package com.rally.order.sweeper;

import com.rally.order.support.TraceContext;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.repository.OrderRepository;
import com.rally.order.service.NormalOrderService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Profile("prod")
@Component
@RequiredArgsConstructor
public class NormalOrderReservationSweeper {
    private final OrderRepository orderRepository;
    private final NormalOrderService normalOrderService;
    private final Tracer tracer;

    @Value("${sweeper.normal-order.reservation.stale-after}")
    private long staleAfterSeconds;

    @Value("${sweeper.normal-order.reservation.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${sweeper.normal-order.reservation.fixed-delay-ms}")
    @Transactional
    void checkReservingStuck() {
        Instant threshold = Instant.now().minusSeconds(staleAfterSeconds);
        List<Order> staleOrders = orderRepository.lockStaleOrders(OrderStatus.RESERVING.name(), threshold, batchSize);
        for (Order order : staleOrders) {
            log.info("Expiring order {} stuck in RESERVING since {}", order.getId(), order.getStatusUpdatedAt());
            UUID correlationId = UUID.randomUUID();
            TraceContext.put(correlationId);
            Span span = tracer.nextSpan().name("sweeper.normal-order.reservation").start();
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                normalOrderService.expireStuckReservation(order);
            } finally {
                span.end();
                TraceContext.clear();
            }
        }
    }
}