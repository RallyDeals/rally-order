package com.rally.order.service;

import com.rally.order.client.dto.CatalogLookupResponse;
import com.rally.order.client.dto.CatalogProduct;
import com.rally.order.dto.CheckOutOrderRequest;
import com.rally.order.dto.OrderItem;
import com.rally.order.mapper.OrderMapper;
import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.messaging.event.inbound.payment.PaymentSucceeded;
import com.rally.order.messaging.event.outbound.orderEvents.NormalOrderCancelled;
import com.rally.order.messaging.event.outbound.orderEvents.OrderCreated;
import com.rally.order.messaging.event.outbound.orderPayments.PaymentChargeRequired;
import com.rally.order.messaging.event.outbound.orderPayments.PaymentTimeout;
import com.rally.order.messaging.outbox.OutboxEventService;
import com.rally.order.messaging.support.EventTypes;
import com.rally.order.model.*;
import com.rally.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class NormalOrderTransitionService {
    private final OrderRepository orderRepository;
    private final OutboxEventService outboxEventService;
    private final OrderMapper orderMapper;

    @Transactional
    Order createReservingOrder(CheckOutOrderRequest request, CatalogLookupResponse catalogLookupResponse, UUID userId) {
        List<OrderProduct> orderProducts = request.getOrderItems().stream().map(item -> {
            CatalogProduct catalogProduct = catalogLookupResponse.getFound().get(item.getProductId());
            return OrderProduct.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .unitPrice(catalogProduct.getBasePrice())
                    .productName(catalogProduct.getName())
                    .productImageUrl(catalogProduct.getImageUrl())
                    .build();
        }).toList();

        BigDecimal totalPrice = orderProducts.stream().map(op -> op.getUnitPrice().multiply(BigDecimal.valueOf(op.getQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder().userId(userId).orderType(OrderType.NORMAL).status(OrderStatus.RESERVING).totalPrice(totalPrice).build();

        orderProducts.forEach(order::addOrderProduct);
        order = orderRepository.save(order);
        return order;
    }

    @Transactional
    Order cancelOrderForInventoryFailure(Order order, CancelReason cancelReason) {
        int updated = orderRepository.updateStatusToCancelledIfCurrent(order.getId(), OrderStatus.RESERVING, cancelReason);
        if (updated == 0) return orderRepository.findById(order.getId()).orElse(order);
        return cancelOrder(order, cancelReason);
    }

    @Transactional
    Order prepareOrderForCharge(Order order, UUID userId, String paymentMethodId) {
        int updated = orderRepository.updateStatusIfCurrent(order.getId(), OrderStatus.RESERVING, OrderStatus.PENDING_CHARGE);
        if (updated == 0)
            return orderRepository.findById(order.getId()).orElse(order);
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
        return order;
    }

    @Transactional
    void confirmOrderForPaymentCharge(PaymentSucceeded eventPayload) {
        int updated = orderRepository.updateStatusWithPaymentIfCurrent(eventPayload.orderId(), OrderStatus.PENDING_CHARGE, OrderStatus.CONFIRMED, eventPayload.paymentId(), eventPayload.paymentIntentId());
        if (updated == 0) return;
        Order order = orderRepository.getReferenceById(eventPayload.orderId());
        outboxEventService.publish(
                "Order",
                eventPayload.orderId(),
                EventTypes.ORDER_CREATED,
                KafkaTopics.ORDER_EVENTS,
                OrderCreated.builder()
                        .orderId(eventPayload.orderId())
                        .userId(order.getUserId())
                        .items(orderMapper.toOrderItems(order.getOrderProducts()))
                        .build()
        );
    }

    @Transactional
    void cancelOrderForPaymentFailure(PaymentFailed eventPayload) {
        Order order = orderRepository.getReferenceById(eventPayload.orderId());
        int updated = orderRepository.updateStatusToCancelledWithPaymentIfCurrent(order.getId(), OrderStatus.PENDING_CHARGE,
                CancelReason.PAYMENT_DECLINED, eventPayload.paymentId(), eventPayload.paymentIntentId());
        if (updated == 0) return;
        order.setPaymentId(eventPayload.paymentId());
        order.setPaymentIntentId(eventPayload.paymentIntentId());
        cancelOrder(order, CancelReason.PAYMENT_DECLINED);
    }

    @Transactional
    void cancelOrderForPaymentTimeout(Order order){
        int updated = orderRepository.updateStatusToCancelledIfCurrent(order.getId(), OrderStatus.PENDING_CHARGE, CancelReason.PAYMENT_TIMEOUT);
        if (updated == 0)
            return;
        cancelOrder(order, CancelReason.PAYMENT_TIMEOUT);
        outboxEventService.publish(
                "Order",
                order.getId(),
                EventTypes.ORDER_PAYMENT_PAYMENT_TIMEOUT,
                KafkaTopics.ORDER_PAYMENTS,
                PaymentTimeout.builder().orderId(order.getId()).build()
        );
    }

    private Order cancelOrder(Order order, CancelReason cancelReason) {
        List<OrderItem> orderItems = orderMapper.toOrderItems(order.getOrderProducts());
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
        return order;
    }
}
