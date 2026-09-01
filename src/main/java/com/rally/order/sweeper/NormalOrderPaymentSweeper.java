package com.rally.order.sweeper;

import com.rally.order.messaging.support.TraceContext;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.repository.OrderRepository;
import com.rally.order.service.NormalOrderService;
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
public class NormalOrderPaymentSweeper {
    private final OrderRepository orderRepository;
    private final NormalOrderService normalOrderService;

    @Value("${sweeper.normal-order.payment.stale-after}")
    private long paymentStaleAfterSeconds;

    @Value("${sweeper.normal-order.payment.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${sweeper.normal-order.payment.fixed-delay-ms}")
    @Transactional
    public void sweepStalePayments() {
        Instant threshold = Instant.now().minusSeconds(paymentStaleAfterSeconds);
        List<Order> staleOrders = orderRepository.lockStaleOrders(OrderStatus.PENDING_CHARGE.name(), threshold, batchSize);
        for (Order order : staleOrders) {
            log.info("Expiring order {} stuck in PENDING_CHARGE since {}", order.getId(), order.getStatusUpdatedAt());
            UUID correlationId = UUID.randomUUID();
            TraceContext.put(correlationId);
            try {
                normalOrderService.expireStuckCharge(order);
            } finally {
                TraceContext.clear();
            }
        }
    }
}
