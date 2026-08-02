package com.rally.order.sweeper;

import com.rally.order.messaging.support.TraceContext;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.repository.OrderRepository;
import com.rally.order.service.NormalOrderService;
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

    @Value("${sweeper.normal-order.reservation.stale-after}")
    private long staleAfterSeconds;

    @Value("${sweeper.normal-order.reservation.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${sweeper.normal-order.reservation.fixed-delay-ms}")
    @Transactional
    void checkReservingStuck() {
        Instant threshold = Instant.now().minusSeconds(staleAfterSeconds);
        List<Order> staleOrders = orderRepository.lockStaleOrders(OrderStatus.RESERVING.name(), threshold, batchSize);
        log.info("Found {} stale orders stuck in RESERVING since {}", staleOrders.size(), threshold);
        for (Order order : staleOrders) {
            log.info("Expiring order {} stuck in RESERVING since {}", order.getId(), order.getStatusUpdatedAt());
            UUID correlationId = UUID.randomUUID();
            TraceContext.put(correlationId, correlationId, correlationId);
            try {
                normalOrderService.expireStuckReservation(order);
            } finally {
                TraceContext.clear();
            }
        }
    }
}