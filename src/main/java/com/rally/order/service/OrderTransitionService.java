package com.rally.order.service;

import com.rally.order.client.dto.CatalogLookupResponse;
import com.rally.order.dto.CheckOutOrderRequest;
import com.rally.order.dto.OrderItem;
import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.event.inbound.payment.PaymentSucceeded;
import com.rally.order.messaging.event.outbound.orderEvents.NormalOrderCancelled;
import com.rally.order.messaging.event.outbound.orderEvents.OrderCreated;
import com.rally.order.messaging.event.outbound.orderPayments.PaymentChargeRequired;
import com.rally.order.messaging.outbox.OutboxEventService;
import com.rally.order.messaging.support.EventTypes;
import com.rally.order.model.CancelReason;
import com.rally.order.model.Order;
import com.rally.order.model.OrderProduct;
import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import com.rally.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class OrderTransitionService {
    private final OrderRepository orderRepository;
    private final OutboxEventService outboxEventService;

    @Transactional
    Order createReservingOrder(CheckOutOrderRequest request, CatalogLookupResponse catalogLookupResponse, UUID userId) {
        List<OrderProduct> orderProducts = request.getOrderItems().stream().map(item -> OrderProduct.builder().productId(item.getProductId()).quantity(item.getQuantity()).unitPrice(catalogLookupResponse.getFound().get(item.getProductId())).build()).toList();

        BigDecimal totalPrice = orderProducts.stream().map(op -> op.getUnitPrice().multiply(BigDecimal.valueOf(op.getQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder().userId(userId).orderType(OrderType.NORMAL).status(OrderStatus.RESERVING).totalPrice(totalPrice).build();

        orderProducts.forEach(order::addOrderProduct);
        order = orderRepository.save(order);
        return order;
    }

    @Transactional
    void cancelOrder(Order order, List<OrderItem> orderItems, CancelReason cancelReason) {
        int updated = orderRepository.updateStatusToCancelledIfCurrent(order.getId(), OrderStatus.RESERVING, OrderStatus.CANCELLED, cancelReason);
        if (updated == 0)
            return;
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(cancelReason);
        outboxEventService.publish("Order", order.getId(), EventTypes.ORDER_NORMAL_CANCELLED,
                KafkaTopics.ORDER_EVENTS,
                NormalOrderCancelled.builder()
                        .orderId(order.getId())
                        .userId(order.getUserId())
                        .items(orderItems)
                        .cancelReason(cancelReason)
                        .build()
        );
    }

    @Transactional
    void prepareOrderForCharge(Order order, UUID userId, String paymentMethodId) {
        int updated = orderRepository.updateStatusIfCurrent(order.getId(), OrderStatus.RESERVING, OrderStatus.PENDING_CHARGE);
        if (updated == 0)
            return;
        order.setStatus(OrderStatus.PENDING_CHARGE);
        outboxEventService.publish("Order", order.getId(), EventTypes.ORDER_PAYMENT_CHARGE_REQUIRED,
                KafkaTopics.ORDER_PAYMENTS,
                PaymentChargeRequired.builder()
                        .userId(userId)
                        .orderId(order.getId())
                        .amount(order.getTotalPrice())
                        .paymentMethodId(paymentMethodId)
                        .build()
        );
    }

    @Transactional
    void setOrderCharged(PaymentSucceeded eventPayload){
        int updated = orderRepository.updateStatusIfCurrent(eventPayload.orderId(), OrderStatus.PENDING_CHARGE, OrderStatus.CONFIRMED);
        if(updated == 0) return;
        Order order = orderRepository.getReferenceById(eventPayload.orderId());
        outboxEventService.publish(
                "Order",
                eventPayload.orderId(),
                EventTypes.ORDER_CREATED,
                KafkaTopics.ORDER_EVENTS,
                OrderCreated.builder()
                        .orderId(eventPayload.orderId())
                        .userId(order.getUserId())
                        .items(order.getOrderProducts().stream().map(
                                op -> OrderItem.builder()
                                        .productId(op.getProductId())
                                        .quantity(op.getQuantity())
                                        .build()).toList())
                        .build()
        );
    }
}
