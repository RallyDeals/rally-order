package com.rally.order.service;

import com.rally.order.client.PaymentServiceClient;
import com.rally.order.client.dto.CatalogLookupResponse;
import com.rally.order.client.dto.CatalogProduct;
import com.rally.order.client.dto.PaymentMethodDetails;
import com.rally.order.dto.CheckOutOrderRequest;
import com.rally.order.dto.OrderProductResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class NormalOrderTransitionService {
    private final OrderRepository orderRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final OutboxEventService outboxEventService;
    private final OrderMapper orderMapper;

    @Transactional
    Order createReservingOrder(CheckOutOrderRequest request, CatalogLookupResponse catalogLookupResponse, UUID userId) {
        List<OrderProduct> orderProducts = request.getOrderItems().stream().map(item -> {
            CatalogProduct catalogProduct = catalogLookupResponse.getFound().get(item.getProductId());
            return OrderProduct.builder()
                    .productId(item.getProductId())
                    .sellerId(catalogProduct.getSellerId())
                    .quantity(item.getQuantity())
                    .unitPrice(catalogProduct.getBasePrice())
                    .productName(catalogProduct.getName())
                    .productImageUrl(catalogProduct.getImageUrl())
                    .build();
        }).toList();

        BigDecimal totalPrice = orderProducts.stream().map(op -> op.getUnitPrice().multiply(BigDecimal.valueOf(op.getQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);

        PaymentMethodDetails cardDetails = paymentServiceClient.getPaymentMethodDetails(userId, request.getPaymentMethodId());

        Order order = Order.builder().userId(userId).orderType(OrderType.NORMAL).status(OrderStatus.RESERVING).totalPrice(totalPrice)
                .address(request.getAddress())
                .cardLast4(cardDetails.getCardLast4())
                .cardBrand(cardDetails.getCardBrand())
                .cardExpMonth(cardDetails.getCardExpMonth())
                .cardExpYear(cardDetails.getCardExpYear())
                .build();

        orderProducts.forEach(order::addOrderProduct);
        order = orderRepository.save(order);
        log.info("Created order {} in RESERVING status for user {}", order.getId(), userId);
        return order;
    }

    @Transactional
    Order cancelOrderForInventoryFailure(Order order, CancelReason cancelReason) {
        int updated = orderRepository.updateStatusToCancelledIfCurrent(order.getId(), OrderStatus.RESERVING, cancelReason);
        if (updated == 0) {
            log.debug("Skipped cancelling order {} for inventory failure, no longer in RESERVING status", order.getId());
            return orderRepository.findById(order.getId()).orElse(order);
        }
        return cancelOrder(order, cancelReason);
    }

    @Transactional
    Order prepareOrderForCharge(Order order, UUID userId, String paymentMethodId) {
        int updated = orderRepository.updateStatusIfCurrent(order.getId(), OrderStatus.RESERVING, OrderStatus.PENDING_CHARGE);
        if (updated == 0) {
            log.debug("Skipped moving order {} to PENDING_CHARGE, no longer in RESERVING status", order.getId());
            return orderRepository.findById(order.getId()).orElse(order);
        }
        order.setStatus(OrderStatus.PENDING_CHARGE);
        log.info("Order {} moved to PENDING_CHARGE, requesting payment charge", order.getId());
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
        int updated = orderRepository.updateStatusWithPaymentIfCurrent(eventPayload.orderId(), OrderStatus.PENDING_CHARGE, OrderStatus.CONFIRMED, eventPayload.paymentId());
        if (updated == 0) {
            log.debug("Skipped confirming order {} for payment charge, no longer in PENDING_CHARGE status", eventPayload.orderId());
            return;
        }
        orderRepository.initializeShippingStatusIfNull(eventPayload.orderId());
        Order order = orderRepository.getReferenceById(eventPayload.orderId());
        log.info("Order {} confirmed after payment charge, paymentId={}", order.getId(), eventPayload.paymentId());
        outboxEventService.publish(
                "Order",
                eventPayload.orderId(),
                EventTypes.ORDER_CREATED,
                KafkaTopics.ORDER_EVENTS,
                OrderCreated.builder()
                        .orderId(eventPayload.orderId())
                        .userId(order.getUserId())
                        .items(orderMapper.toOrderProductResponses(order.getOrderProducts()))
                        .totalPrice(order.getTotalPrice())
                        .address(order.getAddress())
                        .build()
        );
    }

    @Transactional
    void cancelOrderForPaymentFailure(PaymentFailed eventPayload) {
        Order order = orderRepository.getReferenceById(eventPayload.orderId());
        int updated = orderRepository.updateStatusToCancelledWithPaymentIfCurrent(order.getId(), OrderStatus.PENDING_CHARGE,
                CancelReason.PAYMENT_DECLINED, eventPayload.paymentId());
        if (updated == 0) {
            log.debug("Skipped cancelling order {} for payment failure, no longer in PENDING_CHARGE status", order.getId());
            return;
        }
        order.setPaymentId(eventPayload.paymentId());
        order.setPaymentErrorCode(eventPayload.errorCode());
        order.setPaymentErrorMessage(eventPayload.errorMessage());
        cancelOrder(order, CancelReason.PAYMENT_DECLINED);
    }

    @Transactional
    void cancelOrderForPaymentTimeout(Order order){
        int updated = orderRepository.updateStatusToCancelledIfCurrent(order.getId(), OrderStatus.PENDING_CHARGE, CancelReason.PAYMENT_TIMEOUT);
        if (updated == 0) {
            log.debug("Skipped cancelling order {} for payment timeout, no longer in PENDING_CHARGE status", order.getId());
            return;
        }
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
        List<OrderProductResponse> orderItems = orderMapper.toOrderProductResponses(order.getOrderProducts());
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(cancelReason);
        log.info("Order {} cancelled, reason={}", order.getId(), cancelReason);
        outboxEventService.publish("Order", order.getId(), EventTypes.ORDER_NORMAL_CANCELLED,
                KafkaTopics.ORDER_EVENTS,
                NormalOrderCancelled.builder()
                        .orderId(order.getId())
                        .userId(order.getUserId())
                        .items(orderItems)
                        .cancelReason(cancelReason)
                        .totalPrice(order.getTotalPrice())
                        .paymentErrorMessage(order.getPaymentErrorMessage())
                        .build()
        );
        return order;
    }
}
