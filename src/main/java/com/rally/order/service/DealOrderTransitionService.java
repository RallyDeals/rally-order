package com.rally.order.service;

import com.rally.common.exceptions.domain.order.OrderNotFoundException;
import com.rally.order.client.DealServiceClient;
import com.rally.order.client.PaymentServiceClient;
import com.rally.order.client.dto.CatalogProduct;
import com.rally.order.client.dto.PaymentMethodDetails;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
class DealOrderTransitionService {
    private final OrderRepository orderRepository;
    private final DealServiceClient dealServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final OutboxEventService outboxEventService;
    private final OrderMapper orderMapper;

    @Transactional
    void handleParticipantJoined(ParticipantJoined eventPayload, CatalogProduct catalogProduct){
        PaymentMethodDetails cardDetails = paymentServiceClient.getPaymentMethodDetails(eventPayload.userId(), eventPayload.paymentMethodId());
        OrderProduct product = OrderProduct.builder()
                .productId(eventPayload.productId())
                .sellerId(catalogProduct != null ? catalogProduct.getSellerId() : null)
                .quantity(1)
                .unitPrice(eventPayload.price())
                .productName(catalogProduct != null ? catalogProduct.getName() : null)
                .productImageUrl(catalogProduct != null ? catalogProduct.getImageUrl() : null)
                .build();
        Order order = Order.builder()
                .userId(eventPayload.userId())
                .participantId(eventPayload.participantId())
                .dealId(eventPayload.dealId())
                .orderType(OrderType.DEAL)
                .status(OrderStatus.PENDING_AUTHORIZATION)
                .totalPrice(eventPayload.price())
                .address(eventPayload.address())
                .cardBrand(cardDetails.getCardBrand())
                .cardLast4(cardDetails.getCardLast4())
                .cardExpMonth(cardDetails.getCardExpMonth())
                .cardExpYear(cardDetails.getCardExpYear())
                .build();
        order.addOrderProduct(product);
        order = orderRepository.save(order);
        log.info("Created order {} in PENDING_AUTHORIZATION for participant {} in deal {}", order.getId(), eventPayload.participantId(), eventPayload.dealId());
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
        int updated = orderRepository.updateStatusToCancelledWithPaymentIfCurrent(
                eventPayload.orderId(),
                OrderStatus.PENDING_AUTHORIZATION,
                CancelReason.PAYMENT_DECLINED,
                eventPayload.paymentId()
        );
        if (updated == 0) {
            if (!orderRepository.existsById(eventPayload.orderId()))
                throw new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId());
            log.debug("Skipped cancelling order {} for failed authorization, no longer in PENDING_AUTHORIZATION status", eventPayload.orderId());
            return;
        }
        Order order = orderRepository.findById(eventPayload.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId()));
        order.setPaymentErrorCode(eventPayload.errorCode());
        order.setPaymentErrorMessage(eventPayload.errorMessage());
        log.info("Order {} cancelled, reason=PAYMENT_DECLINED", order.getId());
        dealServiceClient.releaseSlot(order.getDealId(), order.getId());
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
        if (updated == 0) {
            log.debug("Skipped moving order {} to PENDING_VOID for participant leave, no longer in AUTHORIZED status", order.getId());
            return;
        }
        log.info("Order {} moved to PENDING_VOID, reason=PARTICIPANT_LEFT", order.getId());
        publishPaymentVoidRequired(order);
    }

    @Transactional
    void handlePaymentAuthorized(PaymentSucceeded eventPayload){
        int updated = orderRepository.updateStatusWithPaymentIfCurrent(
                eventPayload.orderId(),
                OrderStatus.PENDING_AUTHORIZATION,
                OrderStatus.AUTHORIZED,
                eventPayload.paymentId()
        );
        if(updated == 0) {
            log.debug("Skipped authorizing order {}, no longer in PENDING_AUTHORIZATION status", eventPayload.orderId());
            return;
        }
        Order order = orderRepository.findById(eventPayload.orderId()).orElseThrow(
                () -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId())
        );
        boolean slotClaimed = dealServiceClient.authorizeSlot(order.getDealId(), order.getId());
        if (!slotClaimed) {
            log.info("Order {} authorized but deal slot no longer claimable, cancelling", order.getId());
            cancelOrderForLateAuthorization(order);
            return;
        }
        log.info("Order {} moved to AUTHORIZED, paymentId={}", order.getId(), eventPayload.paymentId());
        outboxEventService.publish(
                "Order",
                order.getId(),
                EventTypes.ORDER_AUTHORIZED,
                KafkaTopics.ORDER_EVENTS,
                OrderAuthorized.builder()
                        .orderId(order.getId())
                        .dealId(order.getDealId())
                        .userId(order.getUserId())
                        .totalPrice(order.getTotalPrice())
                        .build()
        );
    }

    private void cancelOrderForLateAuthorization(Order order){
        int updated = orderRepository.updateStatusToPendingVoidIfCurrent(
                order.getId(),
                OrderStatus.AUTHORIZED,
                CancelReason.DEAL_RESOLVED
        );
        if (updated == 0) {
            log.debug("Skipped moving order {} to PENDING_VOID for late authorization, no longer in AUTHORIZED status", order.getId());
            return;
        }
        log.info("Order {} moved to PENDING_VOID, reason=DEAL_RESOLVED", order.getId());
        dealServiceClient.releaseSlot(order.getDealId(), order.getId());
        publishPaymentVoidRequired(order);
    }

    @Transactional
    void handlePaymentCaptured(PaymentSucceeded eventPayload){
        int updated = orderRepository.updateStatusWithPaymentIfCurrent(
                eventPayload.orderId(),
                OrderStatus.PENDING_CAPTURE,
                OrderStatus.CONFIRMED,
                eventPayload.paymentId()
        );
        if (updated == 0) {
            log.debug("Skipped confirming order {} for payment capture, no longer in PENDING_CAPTURE status", eventPayload.orderId());
            return;
        }
        orderRepository.initializeShippingStatusIfNull(eventPayload.orderId());
        Order order = orderRepository.findById(eventPayload.orderId()).orElseThrow(
                () -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId())
        );
        log.info("Order {} confirmed after payment capture, paymentId={}", order.getId(), eventPayload.paymentId());
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
    void handlePaymentVoided(PaymentSucceeded eventPayload){
        int updated = orderRepository.updateStatusWithPaymentIfCurrent(
                eventPayload.orderId(),
                OrderStatus.PENDING_VOID,
                OrderStatus.CANCELLED,
                eventPayload.paymentId()
        );
        if (updated == 0) {
            log.debug("Skipped cancelling order {} for payment void, no longer in PENDING_VOID status", eventPayload.orderId());
            return;
        }
        Order order = orderRepository.findById(eventPayload.orderId()).orElseThrow(
                () -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId())
        );
        if (order.getCancelReason() == CancelReason.PARTICIPANT_LEFT)
            dealServiceClient.releaseAuthorizedSlot(order.getDealId(), order.getId());
        log.info("Order {} cancelled, reason={}", order.getId(), order.getCancelReason());
        publishDealOrderCancelled(order, order.getCancelReason());
    }

    @Transactional
    void handleDealSucceeded(Order order){
        int updated = orderRepository.updateStatusIfCurrent(
                order.getId(),
                OrderStatus.AUTHORIZED,
                OrderStatus.PENDING_CAPTURE
        );
        if (updated == 0) {
            log.debug("Skipped moving order {} to PENDING_CAPTURE, no longer in AUTHORIZED status", order.getId());
            return;
        }
        log.info("Order {} moved to PENDING_CAPTURE after deal succeeded", order.getId());
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
        if (updated == 0) {
            log.debug("Skipped moving order {} to PENDING_VOID for deal failure, no longer in AUTHORIZED status", order.getId());
            return;
        }
        log.info("Order {} moved to PENDING_VOID, reason=DEAL_FAILED", order.getId());
        publishPaymentVoidRequired(order);
    }

    @Transactional
    void cancelOrderForPaymentTimeout(Order order){
        int updated = orderRepository.updateStatusToCancelledIfCurrent(
                order.getId(),
                OrderStatus.PENDING_AUTHORIZATION,
                CancelReason.PAYMENT_TIMEOUT
        );
        if (updated == 0) {
            log.debug("Skipped cancelling order {} for payment timeout, no longer in PENDING_AUTHORIZATION status", order.getId());
            return;
        }
        log.info("Order {} cancelled, reason=PAYMENT_TIMEOUT", order.getId());
        dealServiceClient.releaseSlot(order.getDealId(), order.getId());
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
                        .reason(cancelReason)
                        .items(orderMapper.toOrderProductResponses(order.getOrderProducts()))
                        .totalPrice(order.getTotalPrice())
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
