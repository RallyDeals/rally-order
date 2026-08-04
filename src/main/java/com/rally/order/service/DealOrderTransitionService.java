package com.rally.order.service;

import com.rally.common.exceptions.domain.order.OrderNotFoundException;
import com.rally.order.client.DealServiceClient;
import com.rally.order.mapper.OrderMapper;
import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.event.inbound.participation.ParticipantJoined;
import com.rally.order.messaging.event.inbound.participation.ParticipantLeft;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.messaging.event.inbound.payment.PaymentSucceeded;
import com.rally.order.messaging.event.outbound.orderEvents.DealOrderCancelled;
import com.rally.order.messaging.event.outbound.orderEvents.OrderAuthorized;
import com.rally.order.messaging.event.outbound.orderEvents.OrderCreated;
import com.rally.order.messaging.event.outbound.orderPayments.*;
import com.rally.order.messaging.outbox.OutboxEventService;
import com.rally.order.messaging.support.EventTypes;
import com.rally.order.model.*;
import com.rally.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
class DealOrderTransitionService {
    private final OrderRepository orderRepository;
    private final DealServiceClient dealServiceClient;
    private final OutboxEventService outboxEventService;
    private final OrderMapper orderMapper;

    @Transactional
    void handleParticipantJoined(ParticipantJoined eventPayload){
        Order order = Order.builder()
                .userId(eventPayload.userId())
                .participantId(eventPayload.participantId())
                .dealId(eventPayload.dealId())
                .orderType(OrderType.DEAL)
                .status(OrderStatus.PENDING_AUTHORIZATION)
                .totalPrice(eventPayload.price())
                .build();

        OrderProduct product = OrderProduct.builder()
                .productId(eventPayload.productId())
                .quantity(1)
                .unitPrice(eventPayload.price())
                .build();
        order.addOrderProduct(product);
        order = orderRepository.save(order);

        outboxEventService.publish(
                "Order",
                order.getId(),
                EventTypes.ORDER_PAYMENT_AUTHORIZE_REQUIRED,
                KafkaTopics.ORDER_PAYMENTS,
                PaymentAuthorizeRequired.builder()
                        .orderId(order.getId())
                        .userId(eventPayload.userId())
                        .amount(eventPayload.price())
                        .paymentMethodId(eventPayload.paymentMethodId())
                        .build()
        );
    }

    @Transactional
    void handleFailedAuthorization(PaymentFailed eventPayload){
        Order order = orderRepository.findById(eventPayload.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId()));
        int updated = orderRepository.updateStatusToCancelledWithPaymentIfCurrent(
                order.getId(),
                OrderStatus.PENDING_AUTHORIZATION,
                CancelReason.PAYMENT_DECLINED,
                eventPayload.paymentId(),
                eventPayload.paymentIntentId()
        );
        if (updated == 0) return;
        dealServiceClient.releaseSlot(order.getDealId());
        publishDealOrderCancelled(order, CancelReason.PAYMENT_DECLINED);
    }

    @Transactional
    void handleParticipantLeave(ParticipantLeft eventPayload){
        Order order = orderRepository.findOrderByDealIdAndParticipantId(eventPayload.dealId(), eventPayload.participantId());
        if (order == null)
            throw new OrderNotFoundException("Order not found for participant: " + eventPayload.participantId());
        int updated = orderRepository.updateStatusToPendingVoidIfCurrent(
                order.getId(),
                OrderStatus.AUTHORIZED,
                CancelReason.PARTICIPANT_LEFT
        );
        if (updated == 0) return;
        publishPaymentVoidRequired(order);
    }

    @Transactional
    void handlePaymentAuthorized(PaymentSucceeded eventPayload){
        int updated = orderRepository.updateStatusWithPaymentIfCurrent(
                eventPayload.orderId(),
                OrderStatus.PENDING_AUTHORIZATION,
                OrderStatus.AUTHORIZED,
                eventPayload.paymentId(),
                eventPayload.paymentIntentId()
        );
        if(updated == 0) return;
        Order order = orderRepository.findById(eventPayload.orderId()).orElseThrow(
                () -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId())
        );

        boolean slotClaimed = dealServiceClient.authorizeSlot(order.getDealId());
        if (!slotClaimed) {
            cancelOrderForLateAuthorization(order);
            return;
        }

        outboxEventService.publish(
                "Order",
                order.getId(),
                EventTypes.ORDER_AUTHORIZED,
                KafkaTopics.ORDER_EVENTS,
                OrderAuthorized.builder()
                        .dealId(order.getDealId())
                        .userId(order.getUserId())
                        .build()
        );
    }

    private void cancelOrderForLateAuthorization(Order order){
        int updated = orderRepository.updateStatusToPendingVoidIfCurrent(
                order.getId(),
                OrderStatus.AUTHORIZED,
                CancelReason.DEAL_RESOLVED
        );
        if (updated == 0) return;
        dealServiceClient.releaseSlot(order.getDealId());
        publishPaymentVoidRequired(order);
    }

    @Transactional
    void handlePaymentCaptured(PaymentSucceeded eventPayload){
        int updated = orderRepository.updateStatusWithPaymentIfCurrent(
                eventPayload.orderId(),
                OrderStatus.PENDING_CAPTURE,
                OrderStatus.CONFIRMED,
                eventPayload.paymentId(),
                eventPayload.paymentIntentId()
        );
        if (updated == 0) return;
        Order order = orderRepository.findById(eventPayload.orderId()).orElseThrow(
                () -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId())
        );
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
    void handlePaymentVoided(PaymentSucceeded eventPayload){
        int updated = orderRepository.updateStatusWithPaymentIfCurrent(
                eventPayload.orderId(),
                OrderStatus.PENDING_VOID,
                OrderStatus.CANCELLED,
                eventPayload.paymentId(),
                eventPayload.paymentIntentId()
        );
        if (updated == 0) return;
        Order order = orderRepository.findById(eventPayload.orderId()).orElseThrow(
                () -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId())
        );
        if (order.getCancelReason() == CancelReason.PARTICIPANT_LEFT)
            dealServiceClient.releaseAuthorizedSlot(order.getDealId());
        publishDealOrderCancelled(order, order.getCancelReason());
    }

    @Transactional
    void handleDealSucceeded(Order order){
        int updated = orderRepository.updateStatusIfCurrent(
                order.getId(),
                OrderStatus.AUTHORIZED,
                OrderStatus.PENDING_CAPTURE
        );
        if (updated == 0) return;
        outboxEventService.publish(
                "Order",
                order.getId(),
                EventTypes.ORDER_PAYMENT_CAPTURE_REQUESTED,
                KafkaTopics.ORDER_PAYMENTS,
                PaymentCaptureRequired.builder()
                        .orderId(order.getId())
                        .paymentId(order.getPaymentId())
                        .build()
        );
    }

    @Transactional
    void handleDealFailed(Order order){
        int updated = orderRepository.updateStatusToPendingVoidIfCurrent(
                order.getId(),
                OrderStatus.AUTHORIZED,
                CancelReason.DEAL_FAILED
        );
        if (updated == 0) return;
        publishPaymentVoidRequired(order);
    }

    @Transactional
    void republishCapture(Order order){
        outboxEventService.publish(
                "Order",
                order.getId(),
                EventTypes.ORDER_PAYMENT_CAPTURE_REQUESTED,
                KafkaTopics.ORDER_PAYMENTS,
                PaymentCaptureRequired.builder()
                        .orderId(order.getId())
                        .paymentId(order.getPaymentId())
                        .build()
        );
    }

    @Transactional
    void republishVoid(Order order){
        publishPaymentVoidRequired(order);
    }

    @Transactional
    void cancelOrderForPaymentTimeout(Order order){
        int updated = orderRepository.updateStatusToCancelledIfCurrent(
                order.getId(),
                OrderStatus.PENDING_AUTHORIZATION,
                CancelReason.PAYMENT_TIMEOUT
        );
        if (updated == 0)
            return;
        dealServiceClient.releaseSlot(order.getDealId());
        publishDealOrderCancelled(order, CancelReason.PAYMENT_TIMEOUT);
        publishPaymentTimeout(order);
    }

    private void publishDealOrderCancelled(Order order, CancelReason cancelReason){
        outboxEventService.publish(
                "Order",
                order.getId(),
                EventTypes.ORDER_DEAL_CANCELLED,
                KafkaTopics.ORDER_EVENTS,
                DealOrderCancelled.builder()
                        .orderId(order.getId())
                        .userId(order.getUserId())
                        .dealId(order.getDealId())
                        .participantId(order.getParticipantId())
                        .reason(cancelReason.name())
                        .build()
        );
    }

    private void publishPaymentVoidRequired(Order order){
        outboxEventService.publish(
                "Order",
                order.getId(),
                EventTypes.ORDER_PAYMENT_VOID_REQUESTED,
                KafkaTopics.ORDER_PAYMENTS,
                PaymentVoidRequired.builder()
                        .orderId(order.getId())
                        .paymentId(order.getPaymentId())
                        .build()
        );
    }

    private void publishPaymentTimeout(Order order){
        outboxEventService.publish(
                "Order",
                order.getId(),
                EventTypes.ORDER_PAYMENT_PAYMENT_TIMEOUT,
                KafkaTopics.ORDER_PAYMENTS,
                PaymentTimeout.builder()
                        .orderId(order.getId())
                        .build()
        );
    }
}
