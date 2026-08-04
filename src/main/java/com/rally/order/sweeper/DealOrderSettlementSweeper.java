package com.rally.order.sweeper;

import com.rally.order.messaging.support.TraceContext;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.repository.OrderRepository;
import com.rally.order.service.DealOrderService;
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
public class DealOrderSettlementSweeper {
    private final OrderRepository orderRepository;
    private final DealOrderService dealOrderService;

    @Value("${sweeper.deal-order.settlement.stale-after}")
    private long settlementStaleAfterSeconds;

    @Value("${sweeper.deal-order.settlement.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${sweeper.deal-order.settlement.fixed-delay-ms}")
    @Transactional
    public void sweepStaleSettlements() {
        Instant threshold = Instant.now().minusSeconds(settlementStaleAfterSeconds);

        List<Order> staleCaptures = orderRepository.lockStaleOrders(OrderStatus.PENDING_CAPTURE.name(), threshold, batchSize);
        for (Order order : staleCaptures) {
            log.info("Re-publishing capture for order {} stuck in PENDING_CAPTURE since {}", order.getId(), order.getStatusUpdatedAt());
            withTraceContext(() -> dealOrderService.republishStaleCapture(order));
        }

        List<Order> staleVoids = orderRepository.lockStaleOrders(OrderStatus.PENDING_VOID.name(), threshold, batchSize);
        for (Order order : staleVoids) {
            log.info("Re-publishing void for order {} stuck in PENDING_VOID since {}", order.getId(), order.getStatusUpdatedAt());
            withTraceContext(() -> dealOrderService.republishStaleVoid(order));
        }
    }

    private void withTraceContext(Runnable action) {
        UUID correlationId = UUID.randomUUID();
        TraceContext.put(correlationId, correlationId, correlationId);
        try {
            action.run();
        } finally {
            TraceContext.clear();
        }
    }
}
