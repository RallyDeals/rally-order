package com.rally.order.service;

import com.rally.common.exceptions.domain.order.OrderNotFoundException;
import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.DealServiceClient;
import com.rally.order.client.PaymentServiceClient;
import com.rally.order.client.dto.CatalogProduct;
import com.rally.order.client.dto.PaymentMethodDetails;
import com.rally.order.mapper.OrderMapper;
import com.rally.order.mapper.OrderMapperImpl;
import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.event.inbound.participation.ParticipantJoined;
import com.rally.order.messaging.event.inbound.participation.ParticipantLeft;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.messaging.event.inbound.payment.PaymentSucceeded;
import com.rally.order.messaging.event.outbound.orderEvents.DealOrderCancelled;
import com.rally.order.messaging.event.outbound.orderEvents.OrderAuthorized;
import com.rally.order.messaging.event.outbound.orderEvents.OrderCreated;
import com.rally.order.messaging.event.outbound.orderPayments.PaymentAuthorizeRequired;
import com.rally.order.messaging.event.outbound.orderPayments.PaymentCaptureRequired;
import com.rally.order.messaging.event.outbound.orderPayments.PaymentTimeout;
import com.rally.order.messaging.event.outbound.orderPayments.PaymentVoidRequired;
import com.rally.order.messaging.outbox.OutboxEventService;
import com.rally.order.messaging.support.EventTypes;
import com.rally.order.model.CancelReason;
import com.rally.order.model.Order;
import com.rally.order.model.OrderProduct;
import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import com.rally.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealOrderTransitionServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private DealServiceClient dealServiceClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private OutboxEventService outboxEventService;
    @Spy
    private OrderMapper orderMapper = new OrderMapperImpl();

    @InjectMocks
    private DealOrderTransitionService transitionService;

    private UUID dealId;
    private UUID participantId;
    private UUID userId;
    private UUID orderId;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        dealId = UUID.randomUUID();
        participantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
    }

    // ---- handleParticipantJoined ----

    @Test
    void handleParticipantJoined_withCatalogProduct_savesOrderWithSnapshotAndPublishesAuthorizeRequired() {
        UUID productId = UUID.randomUUID();
        ParticipantJoined event = new ParticipantJoined(participantId, dealId, userId, productId, BigDecimal.valueOf(50), "pm_123", "123 Main St");
        CatalogProduct catalogProduct = CatalogProduct.builder().id(productId).name("Widget").imageUrl("http://img/widget.png").basePrice(BigDecimal.valueOf(50)).build();
        when(paymentServiceClient.getPaymentMethodDetails(userId, "pm_123")).thenReturn(
                PaymentMethodDetails.builder().cardLast4("4242").cardBrand("visa").cardExpMonth("12").cardExpYear("2030").build());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(orderId);
            return o;
        });

        transitionService.handleParticipantJoined(event, catalogProduct);

        verify(paymentServiceClient).getPaymentMethodDetails(userId, "pm_123");
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedOrderForParticipantJoined(productId));

        ArgumentCaptor<PaymentAuthorizeRequired> eventCaptor = ArgumentCaptor.forClass(PaymentAuthorizeRequired.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_PAYMENT_AUTHORIZE_REQUIRED), eq(KafkaTopics.ORDER_PAYMENTS), eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(PaymentAuthorizeRequired.builder().orderId(orderId).userId(userId).amount(BigDecimal.valueOf(50)).paymentMethodId("pm_123").build());
    }

    private Order expectedOrderForParticipantJoined(UUID productId) {
        Order expected = Order.builder()
                .id(orderId)
                .userId(userId)
                .participantId(participantId)
                .dealId(dealId)
                .orderType(OrderType.DEAL)
                .status(OrderStatus.PENDING_AUTHORIZATION)
                .totalPrice(BigDecimal.valueOf(50))
                .address("123 Main St")
                .cardLast4("4242")
                .cardBrand("visa")
                .cardExpMonth("12")
                .cardExpYear("2030")
                .build();
        expected.addOrderProduct(OrderProduct.builder()
                .productId(productId).quantity(1).unitPrice(BigDecimal.valueOf(50))
                .productName("Widget").productImageUrl("http://img/widget.png").build());
        return expected;
    }

    @Test
    void handleParticipantJoined_withNullCatalogProduct_leavesProductNameAndImageNull() {
        UUID productId = UUID.randomUUID();
        ParticipantJoined event = new ParticipantJoined(participantId, dealId, userId, productId, BigDecimal.valueOf(50), "pm_123", "123 Main St");
        when(paymentServiceClient.getPaymentMethodDetails(userId, "pm_123")).thenReturn(
                PaymentMethodDetails.builder().cardLast4("4242").cardBrand("visa").cardExpMonth("12").cardExpYear("2030").build());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        transitionService.handleParticipantJoined(event, null);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        OrderProduct product = orderCaptor.getValue().getOrderProducts().get(0);
        assertNull(product.getProductName());
        assertNull(product.getProductImageUrl());
    }

    @Test
    void handleParticipantJoined_whenPaymentMethodDetailsRetrievalFails_propagatesExceptionWithoutSavingOrder() {
        UUID productId = UUID.randomUUID();
        ParticipantJoined event = new ParticipantJoined(participantId, dealId, userId, productId, BigDecimal.valueOf(50), "pm_123", "123 Main St");
        CatalogProduct catalogProduct = CatalogProduct.builder().id(productId).name("Widget").imageUrl("http://img/widget.png").basePrice(BigDecimal.valueOf(50)).build();
        when(paymentServiceClient.getPaymentMethodDetails(userId, "pm_123"))
                .thenThrow(new ServiceUnavailableException("Payment service is unavailable"));

        assertThrows(ServiceUnavailableException.class, () -> transitionService.handleParticipantJoined(event, catalogProduct));

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(outboxEventService);
    }

    // ---- handleFailedAuthorization ----

    @Test
    void handleFailedAuthorization_whenGuardedUpdateSucceeds_releasesSlotAndPublishesDealOrderCancelled() {
        PaymentFailed event = new PaymentFailed(paymentId, orderId, BigDecimal.valueOf(50), "card_declined", "402");
        Order order = Order.builder().id(orderId).userId(userId).dealId(dealId).participantId(participantId).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateStatusToCancelledWithPaymentIfCurrent(orderId, OrderStatus.PENDING_AUTHORIZATION, CancelReason.PAYMENT_DECLINED, paymentId))
                .thenReturn(1);

        transitionService.handleFailedAuthorization(event);

        verify(dealServiceClient).releaseSlot(dealId);
        ArgumentCaptor<DealOrderCancelled> eventCaptor = ArgumentCaptor.forClass(DealOrderCancelled.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_DEAL_CANCELLED), eq(KafkaTopics.ORDER_EVENTS), eventCaptor.capture());
        assertEquals(CancelReason.PAYMENT_DECLINED, eventCaptor.getValue().reason());
    }

    @Test
    void handleFailedAuthorization_whenRaceLost_doesNothing() {
        PaymentFailed event = new PaymentFailed(paymentId, orderId, BigDecimal.valueOf(50), "card_declined", "402");
        Order order = Order.builder().id(orderId).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateStatusToCancelledWithPaymentIfCurrent(orderId, OrderStatus.PENDING_AUTHORIZATION, CancelReason.PAYMENT_DECLINED, paymentId))
                .thenReturn(0);

        transitionService.handleFailedAuthorization(event);

        verify(dealServiceClient, never()).releaseSlot(any());
        verifyNoInteractions(paymentServiceClient, outboxEventService);
    }

    @Test
    void handleFailedAuthorization_whenOrderNotFound_throwsOrderNotFoundException() {
        PaymentFailed event = new PaymentFailed(paymentId, orderId, BigDecimal.valueOf(50), "card_declined", "402");
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> transitionService.handleFailedAuthorization(event));
    }

    // ---- handleParticipantLeave ----

    @Test
    void handleParticipantLeave_whenGuardedUpdateSucceeds_publishesPaymentVoidRequired() {
        ParticipantLeft event = new ParticipantLeft(participantId, dealId);
        Order order = Order.builder().id(orderId).paymentId(paymentId).build();
        when(orderRepository.findOrderByDealIdAndParticipantId(dealId, participantId)).thenReturn(order);
        when(orderRepository.updateStatusToPendingVoidIfCurrent(orderId, OrderStatus.AUTHORIZED, CancelReason.PARTICIPANT_LEFT)).thenReturn(1);

        transitionService.handleParticipantLeave(event);

        ArgumentCaptor<PaymentVoidRequired> eventCaptor = ArgumentCaptor.forClass(PaymentVoidRequired.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_PAYMENT_VOID_REQUESTED), eq(KafkaTopics.ORDER_PAYMENTS), eventCaptor.capture());
        assertEquals(paymentId, eventCaptor.getValue().paymentId());
    }

    @Test
    void handleParticipantLeave_whenRaceLost_doesNothing() {
        ParticipantLeft event = new ParticipantLeft(participantId, dealId);
        Order order = Order.builder().id(orderId).build();
        when(orderRepository.findOrderByDealIdAndParticipantId(dealId, participantId)).thenReturn(order);
        when(orderRepository.updateStatusToPendingVoidIfCurrent(orderId, OrderStatus.AUTHORIZED, CancelReason.PARTICIPANT_LEFT)).thenReturn(0);

        transitionService.handleParticipantLeave(event);

        verifyNoInteractions(outboxEventService);
    }

    @Test
    void handleParticipantLeave_whenNoMatchingOrder_throwsOrderNotFoundException() {
        ParticipantLeft event = new ParticipantLeft(participantId, dealId);
        when(orderRepository.findOrderByDealIdAndParticipantId(dealId, participantId)).thenReturn(null);

        assertThrows(OrderNotFoundException.class, () -> transitionService.handleParticipantLeave(event));
    }

    // ---- handlePaymentAuthorized ----

    @Test
    void handlePaymentAuthorized_whenSlotClaimed_publishesOrderAuthorized() {
        PaymentSucceeded event = new PaymentSucceeded(paymentId, orderId, BigDecimal.valueOf(50));
        Order order = Order.builder().id(orderId).userId(userId).dealId(dealId).build();
        when(orderRepository.updateStatusWithPaymentIfCurrent(orderId, OrderStatus.PENDING_AUTHORIZATION, OrderStatus.AUTHORIZED, paymentId)).thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(dealServiceClient.authorizeSlot(dealId)).thenReturn(true);

        transitionService.handlePaymentAuthorized(event);

        ArgumentCaptor<OrderAuthorized> eventCaptor = ArgumentCaptor.forClass(OrderAuthorized.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_AUTHORIZED), eq(KafkaTopics.ORDER_EVENTS), eventCaptor.capture());
        assertEquals(dealId, eventCaptor.getValue().dealId());
        assertEquals(userId, eventCaptor.getValue().userId());
        verify(orderRepository, never()).updateStatusToPendingVoidIfCurrent(any(), any(), any());
    }

    @Test
    void handlePaymentAuthorized_whenSlotRejected_parksOrderPendingVoidAndReleasesSlotWithoutFiringOrderAuthorized() {
        PaymentSucceeded event = new PaymentSucceeded(paymentId, orderId, BigDecimal.valueOf(50));
        Order order = Order.builder().id(orderId).userId(userId).dealId(dealId).paymentId(paymentId).build();
        when(orderRepository.updateStatusWithPaymentIfCurrent(orderId, OrderStatus.PENDING_AUTHORIZATION, OrderStatus.AUTHORIZED, paymentId)).thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(dealServiceClient.authorizeSlot(dealId)).thenReturn(false);
        when(orderRepository.updateStatusToPendingVoidIfCurrent(orderId, OrderStatus.AUTHORIZED, CancelReason.DEAL_RESOLVED)).thenReturn(1);

        transitionService.handlePaymentAuthorized(event);

        verify(dealServiceClient).releaseSlot(dealId);
        verify(dealServiceClient, never()).releaseAuthorizedSlot(any());
        ArgumentCaptor<PaymentVoidRequired> eventCaptor = ArgumentCaptor.forClass(PaymentVoidRequired.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_PAYMENT_VOID_REQUESTED), eq(KafkaTopics.ORDER_PAYMENTS), eventCaptor.capture());
        verify(outboxEventService, never()).publish(eq("Order"), any(), eq(EventTypes.ORDER_AUTHORIZED), any(), any());
    }

    @Test
    void handlePaymentAuthorized_whenRaceLost_doesNothingAndNeverCallsDealService() {
        PaymentSucceeded event = new PaymentSucceeded(paymentId, orderId, BigDecimal.valueOf(50));
        when(orderRepository.updateStatusWithPaymentIfCurrent(orderId, OrderStatus.PENDING_AUTHORIZATION, OrderStatus.AUTHORIZED, paymentId)).thenReturn(0);

        transitionService.handlePaymentAuthorized(event);

        verify(orderRepository, never()).findById(any());
        verifyNoInteractions(dealServiceClient, paymentServiceClient, outboxEventService);
    }

    // ---- handlePaymentCaptured ----

    @Test
    void handlePaymentCaptured_whenGuardedUpdateSucceeds_publishesOrderCreatedWithItems() {
        PaymentSucceeded event = new PaymentSucceeded(paymentId, orderId, BigDecimal.valueOf(50));
        Order order = Order.builder().id(orderId).userId(userId).build();
        order.addOrderProduct(OrderProduct.builder().productId(UUID.randomUUID()).quantity(1).unitPrice(BigDecimal.valueOf(50)).build());
        when(orderRepository.updateStatusWithPaymentIfCurrent(orderId, OrderStatus.PENDING_CAPTURE, OrderStatus.CONFIRMED, paymentId)).thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        transitionService.handlePaymentCaptured(event);

        verify(orderRepository).initializeShippingStatusIfNull(orderId);
        ArgumentCaptor<OrderCreated> eventCaptor = ArgumentCaptor.forClass(OrderCreated.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_CREATED), eq(KafkaTopics.ORDER_EVENTS), eventCaptor.capture());
        assertEquals(orderId, eventCaptor.getValue().orderId());
        assertEquals(userId, eventCaptor.getValue().userId());
        assertEquals(1, eventCaptor.getValue().items().size());
        verifyNoInteractions(paymentServiceClient);
    }

    @Test
    void handlePaymentCaptured_whenRaceLost_doesNothing() {
        PaymentSucceeded event = new PaymentSucceeded(paymentId, orderId, BigDecimal.valueOf(50));
        when(orderRepository.updateStatusWithPaymentIfCurrent(orderId, OrderStatus.PENDING_CAPTURE, OrderStatus.CONFIRMED, paymentId)).thenReturn(0);

        transitionService.handlePaymentCaptured(event);

        verify(orderRepository, never()).findById(any());
        verify(orderRepository, never()).initializeShippingStatusIfNull(any());
        verifyNoInteractions(paymentServiceClient, outboxEventService);
    }

    // ---- handlePaymentVoided ----

    @Test
    void handlePaymentVoided_whenParkedByParticipantLeft_releasesAuthorizedSlot() {
        PaymentSucceeded event = new PaymentSucceeded(paymentId, orderId, BigDecimal.valueOf(50));
        Order order = Order.builder().id(orderId).userId(userId).dealId(dealId).participantId(participantId).cancelReason(CancelReason.PARTICIPANT_LEFT).build();
        when(orderRepository.updateStatusWithPaymentIfCurrent(orderId, OrderStatus.PENDING_VOID, OrderStatus.CANCELLED, paymentId)).thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        transitionService.handlePaymentVoided(event);

        verify(dealServiceClient).releaseAuthorizedSlot(dealId);
        ArgumentCaptor<DealOrderCancelled> eventCaptor = ArgumentCaptor.forClass(DealOrderCancelled.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_DEAL_CANCELLED), eq(KafkaTopics.ORDER_EVENTS), eventCaptor.capture());
        assertEquals(CancelReason.PARTICIPANT_LEFT, eventCaptor.getValue().reason());
        verifyNoInteractions(paymentServiceClient);
    }

    @Test
    void handlePaymentVoided_whenParkedByDealFailed_doesNotReleaseAuthorizedSlot() {
        PaymentSucceeded event = new PaymentSucceeded(paymentId, orderId, BigDecimal.valueOf(50));
        Order order = Order.builder().id(orderId).userId(userId).dealId(dealId).cancelReason(CancelReason.DEAL_FAILED).build();
        when(orderRepository.updateStatusWithPaymentIfCurrent(orderId, OrderStatus.PENDING_VOID, OrderStatus.CANCELLED, paymentId)).thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        transitionService.handlePaymentVoided(event);

        verify(dealServiceClient, never()).releaseAuthorizedSlot(any());
        ArgumentCaptor<DealOrderCancelled> eventCaptor = ArgumentCaptor.forClass(DealOrderCancelled.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_DEAL_CANCELLED), eq(KafkaTopics.ORDER_EVENTS), eventCaptor.capture());
        assertEquals(CancelReason.DEAL_FAILED, eventCaptor.getValue().reason());
        verifyNoInteractions(paymentServiceClient);
    }

    @Test
    void handlePaymentVoided_whenRaceLost_doesNothing() {
        PaymentSucceeded event = new PaymentSucceeded(paymentId, orderId, BigDecimal.valueOf(50));
        when(orderRepository.updateStatusWithPaymentIfCurrent(orderId, OrderStatus.PENDING_VOID, OrderStatus.CANCELLED, paymentId)).thenReturn(0);

        transitionService.handlePaymentVoided(event);

        verify(orderRepository, never()).findById(any());
        verifyNoInteractions(dealServiceClient, paymentServiceClient, outboxEventService);
    }

    // ---- handleDealSucceeded / handleDealFailed ----

    @Test
    void handleDealSucceeded_whenGuardedUpdateSucceeds_publishesPaymentCaptureRequired() {
        Order order = Order.builder().id(orderId).paymentId(paymentId).build();
        when(orderRepository.updateStatusIfCurrent(orderId, OrderStatus.AUTHORIZED, OrderStatus.PENDING_CAPTURE)).thenReturn(1);

        transitionService.handleDealSucceeded(order);

        ArgumentCaptor<PaymentCaptureRequired> eventCaptor = ArgumentCaptor.forClass(PaymentCaptureRequired.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_PAYMENT_CAPTURE_REQUESTED), eq(KafkaTopics.ORDER_PAYMENTS), eventCaptor.capture());
        assertEquals(paymentId, eventCaptor.getValue().paymentId());
    }

    @Test
    void handleDealSucceeded_whenRaceLost_doesNothing() {
        Order order = Order.builder().id(orderId).build();
        when(orderRepository.updateStatusIfCurrent(orderId, OrderStatus.AUTHORIZED, OrderStatus.PENDING_CAPTURE)).thenReturn(0);

        transitionService.handleDealSucceeded(order);

        verifyNoInteractions(outboxEventService);
    }

    @Test
    void handleDealFailed_whenGuardedUpdateSucceeds_publishesPaymentVoidRequired() {
        Order order = Order.builder().id(orderId).paymentId(paymentId).build();
        when(orderRepository.updateStatusToPendingVoidIfCurrent(orderId, OrderStatus.AUTHORIZED, CancelReason.DEAL_FAILED)).thenReturn(1);

        transitionService.handleDealFailed(order);

        ArgumentCaptor<PaymentVoidRequired> eventCaptor = ArgumentCaptor.forClass(PaymentVoidRequired.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_PAYMENT_VOID_REQUESTED), eq(KafkaTopics.ORDER_PAYMENTS), eventCaptor.capture());
        assertEquals(paymentId, eventCaptor.getValue().paymentId());
    }

    @Test
    void handleDealFailed_whenRaceLost_doesNothing() {
        Order order = Order.builder().id(orderId).build();
        when(orderRepository.updateStatusToPendingVoidIfCurrent(orderId, OrderStatus.AUTHORIZED, CancelReason.DEAL_FAILED)).thenReturn(0);

        transitionService.handleDealFailed(order);

        verifyNoInteractions(outboxEventService);
    }

    // ---- cancelOrderForPaymentTimeout ----

    @Test
    void cancelOrderForPaymentTimeout_whenGuardedUpdateSucceeds_releasesSlotAndPublishesBothEvents() {
        Order order = Order.builder().id(orderId).userId(userId).dealId(dealId).participantId(participantId).build();
        when(orderRepository.updateStatusToCancelledIfCurrent(orderId, OrderStatus.PENDING_AUTHORIZATION, CancelReason.PAYMENT_TIMEOUT)).thenReturn(1);

        transitionService.cancelOrderForPaymentTimeout(order);

        verify(dealServiceClient).releaseSlot(dealId);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventService, org.mockito.Mockito.times(2)).publish(eq("Order"), eq(orderId), any(), any(), payloadCaptor.capture());
        assertTrue(payloadCaptor.getAllValues().stream().anyMatch(p -> p instanceof DealOrderCancelled));
        assertTrue(payloadCaptor.getAllValues().stream().anyMatch(p -> p instanceof PaymentTimeout));
    }

    @Test
    void cancelOrderForPaymentTimeout_whenRaceLost_doesNothing() {
        Order order = Order.builder().id(orderId).build();
        when(orderRepository.updateStatusToCancelledIfCurrent(orderId, OrderStatus.PENDING_AUTHORIZATION, CancelReason.PAYMENT_TIMEOUT)).thenReturn(0);

        transitionService.cancelOrderForPaymentTimeout(order);

        verifyNoInteractions(dealServiceClient, outboxEventService);
    }
}
