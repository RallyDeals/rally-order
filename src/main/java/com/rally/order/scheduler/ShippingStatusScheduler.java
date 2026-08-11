package com.rally.order.scheduler;

import com.rally.order.model.ShippingStatus;
import com.rally.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingStatusScheduler {
    private final OrderRepository orderRepository;

    @Value("${scheduler.shipping.processing-duration-seconds}")
    long processingDurationSeconds;

    @Value("${scheduler.shipping.shipping-duration-seconds}")
    long shippingDurationSeconds;

    @Scheduled(cron = "${scheduler.shipping.cron}")
    @Transactional
    public void advanceShippingStatuses() {
        OffsetDateTime processingThreshold = OffsetDateTime.now().minusSeconds(processingDurationSeconds);
        int advancedToShipping = orderRepository.advanceShippingStatus(
                ShippingStatus.PROCESSING, ShippingStatus.SHIPPING, processingThreshold);
        log.info("Advanced {} orders from PROCESSING to SHIPPING", advancedToShipping);

        OffsetDateTime shippingThreshold = OffsetDateTime.now().minusSeconds(shippingDurationSeconds);
        int advancedToDelivered = orderRepository.advanceShippingStatus(
                ShippingStatus.SHIPPING, ShippingStatus.DELIVERED, shippingThreshold);
        log.info("Advanced {} orders from SHIPPING to DELIVERED", advancedToDelivered);
    }
}
