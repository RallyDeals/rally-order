package com.rally.order.service;

import com.rally.common.exceptions.domain.order.OrderNotFoundException;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.model.Order;
import com.rally.order.model.OrderType;
import com.rally.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final NormalOrderService normalOrderService;
    private final DealOrderService dealOrderService;

    public void handlePaymentFailed(PaymentFailed eventPayload) {
        Order order = orderRepository.findById(eventPayload.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId()));
        if(order.getOrderType() == OrderType.DEAL)
            dealOrderService.handlePaymentFailed(eventPayload);
        else
            normalOrderService.handlePaymentFailed(eventPayload);
    }
}
