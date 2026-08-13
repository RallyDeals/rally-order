package com.rally.order.service;

import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.PaymentServiceClient;
import com.rally.order.client.dto.CatalogLookupResponse;
import com.rally.order.client.dto.CatalogProduct;
import com.rally.order.client.dto.PaymentMethodDetails;
import com.rally.order.dto.CheckOutOrderRequest;
import com.rally.order.dto.OrderItem;
import com.rally.order.mapper.OrderMapper;
import com.rally.order.mapper.OrderMapperImpl;
import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NormalOrderTransitionServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private OutboxEventService outboxEventService;
    @Spy
    private OrderMapper orderMapper = new OrderMapperImpl();

    @InjectMocks
    private NormalOrderTransitionService orderTransitionService;

    private UUID userId;
    private UUID productId1;
    private UUID productId2;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId1 = UUID.randomUUID();
        productId2 = UUID.randomUUID();
    }

    // ---- createReservingOrder ----

    @Test
    void createReservingOrder_buildsOrderWithComputedTotalPriceAndReturnsPersistedEntity() {
        CheckOutOrderRequest request = checkoutRequest(item(productId1, 2), item(productId2, 3));
        CatalogLookupResponse catalogResponse = CatalogLookupResponse.builder()
                .found(Map.of(
                        productId1, CatalogProduct.builder().id(productId1).name("Widget").imageUrl("http://img/widget.png").basePrice(BigDecimal.TEN).build(),
                        productId2, CatalogProduct.builder().id(productId2).name("Gadget").imageUrl("http://img/gadget.png").basePrice(BigDecimal.valueOf(5)).build()))
                .notFound(List.of())
                .build();

        when(paymentServiceClient.getPaymentMethodDetails(userId, "pm_123")).thenReturn(
                PaymentMethodDetails.builder().cardLast4("4242").cardBrand("visa").cardExpMonth("12").cardExpYear("2030").build());
        Order persistedOrder = Order.builder().id(UUID.randomUUID()).build();
        when(orderRepository.save(any(Order.class))).thenReturn(persistedOrder);

        Order result = orderTransitionService.createReservingOrder(request, catalogResponse, userId);

        assertSame(persistedOrder, result);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedReservingOrder());
    }

    private Order expectedReservingOrder() {
        Order expected = Order.builder()
                .userId(userId)
                .orderType(OrderType.NORMAL)
                .status(OrderStatus.RESERVING)
                .totalPrice(BigDecimal.valueOf(35))
                .address("123 Main St")
                .cardLast4("4242")
                .cardBrand("visa")
                .cardExpMonth("12")
                .cardExpYear("2030")
                .build();
        expected.addOrderProduct(OrderProduct.builder()
                .productId(productId1).quantity(2).unitPrice(BigDecimal.TEN).productName("Widget").productImageUrl("http://img/widget.png").build());
        expected.addOrderProduct(OrderProduct.builder()
                .productId(productId2).quantity(3).unitPrice(BigDecimal.valueOf(5)).productName("Gadget").productImageUrl("http://img/gadget.png").build());
        return expected;
    }

    @Test
    void createReservingOrder_whenPaymentMethodDetailsRetrievalFails_propagatesExceptionWithoutSavingOrder() {
        CheckOutOrderRequest request = checkoutRequest(item(productId1, 2));
        CatalogLookupResponse catalogResponse = CatalogLookupResponse.builder()
                .found(Map.of(productId1, CatalogProduct.builder().id(productId1).name("Widget").imageUrl("http://img/widget.png").basePrice(BigDecimal.TEN).build()))
                .notFound(List.of())
                .build();
        when(paymentServiceClient.getPaymentMethodDetails(userId, "pm_123"))
                .thenThrow(new ServiceUnavailableException("Payment service is unavailable"));

        assertThrows(ServiceUnavailableException.class,
                () -> orderTransitionService.createReservingOrder(request, catalogResponse, userId));

        verify(orderRepository, never()).save(any());
    }

    // ---- cancelOrderForInventoryFailure ----

    @Test
    void cancelOrderForInventoryFailure_whenCurrentStatusMatches_cancelsOrderAndPublishesEvent() {
        Order order = reservingOrderWithProducts();
        when(orderRepository.updateStatusToCancelledIfCurrent(order.getId(), OrderStatus.RESERVING, CancelReason.INSUFFICIENT_STOCK))
                .thenReturn(1);

        Order result = orderTransitionService.cancelOrderForInventoryFailure(order, CancelReason.INSUFFICIENT_STOCK);

        assertSame(order, result);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(CancelReason.INSUFFICIENT_STOCK, order.getCancelReason());

        ArgumentCaptor<NormalOrderCancelled> eventCaptor = ArgumentCaptor.forClass(NormalOrderCancelled.class);
        verify(outboxEventService).publish(eq("Order"), eq(order.getId()), eq(EventTypes.ORDER_NORMAL_CANCELLED), eq(KafkaTopics.ORDER_EVENTS), eventCaptor.capture());
        NormalOrderCancelled published = eventCaptor.getValue();
        assertEquals(order.getId(), published.orderId());
        assertEquals(order.getUserId(), published.userId());
        assertEquals(CancelReason.INSUFFICIENT_STOCK, published.cancelReason());
        assertEquals(2, published.items().size());
        assertTrue(published.items().stream().anyMatch(i -> i.getProductId().equals(productId1) && i.getQuantity() == 2));
        assertTrue(published.items().stream().anyMatch(i -> i.getProductId().equals(productId2) && i.getQuantity() == 1));
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void cancelOrderForInventoryFailure_whenRaceLostAndOrderStillPersisted_returnsCurrentPersistedOrderWithoutPublishing() {
        Order order = reservingOrderWithProducts();
        Order currentPersistedOrder = Order.builder().id(order.getId()).status(OrderStatus.CANCELLED).build();
        when(orderRepository.updateStatusToCancelledIfCurrent(order.getId(), OrderStatus.RESERVING, CancelReason.INSUFFICIENT_STOCK))
                .thenReturn(0);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.ofNullable(currentPersistedOrder));

        Order result = orderTransitionService.cancelOrderForInventoryFailure(order, CancelReason.INSUFFICIENT_STOCK);

        assertSame(currentPersistedOrder, result);
        assertEquals(OrderStatus.RESERVING, order.getStatus());
        verify(outboxEventService, never()).publish(any(), any(), any(), any(), any());
    }

    // ---- prepareOrderForCharge ----

    @Test
    void prepareOrderForCharge_whenCurrentStatusMatches_updatesStatusAndPublishesChargeRequiredEvent() {
        Order order = Order.builder().id(UUID.randomUUID()).userId(userId).status(OrderStatus.RESERVING).totalPrice(BigDecimal.valueOf(35)).build();
        when(orderRepository.updateStatusIfCurrent(order.getId(), OrderStatus.RESERVING, OrderStatus.PENDING_CHARGE)).thenReturn(1);

        Order result = orderTransitionService.prepareOrderForCharge(order, userId, "pm_123");

        assertSame(order, result);
        assertEquals(OrderStatus.PENDING_CHARGE, order.getStatus());

        ArgumentCaptor<PaymentChargeRequired> eventCaptor = ArgumentCaptor.forClass(PaymentChargeRequired.class);
        verify(outboxEventService).publish(eq("Order"), eq(order.getId()), eq(EventTypes.ORDER_PAYMENT_CHARGE_REQUIRED), eq(KafkaTopics.ORDER_PAYMENTS), eventCaptor.capture());
        PaymentChargeRequired published = eventCaptor.getValue();
        assertEquals(userId, published.userId());
        assertEquals(order.getId(), published.orderId());
        assertEquals(order.getTotalPrice(), published.amount());
        assertEquals("pm_123", published.paymentMethodId());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void prepareOrderForCharge_whenRaceLost_returnsCurrentPersistedOrderWithoutPublishing() {
        Order order = Order.builder().id(UUID.randomUUID()).userId(userId).status(OrderStatus.RESERVING).totalPrice(BigDecimal.TEN).build();
        Order currentPersistedOrder = Order.builder().id(order.getId()).status(OrderStatus.CANCELLED).build();
        when(orderRepository.updateStatusIfCurrent(order.getId(), OrderStatus.RESERVING, OrderStatus.PENDING_CHARGE)).thenReturn(0);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(currentPersistedOrder));

        Order result = orderTransitionService.prepareOrderForCharge(order, userId, "pm_123");

        assertSame(currentPersistedOrder, result);
        assertEquals(OrderStatus.RESERVING, order.getStatus());
        verify(outboxEventService, never()).publish(any(), any(), any(), any(), any());
    }

    // ---- confirmOrderForPaymentCharge ----

    @Test
    void confirmOrderForPaymentCharge_whenCurrentStatusMatches_publishesOrderCreatedEventWithOrderItems() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentSucceeded event = new PaymentSucceeded(paymentId, orderId, BigDecimal.valueOf(35));

        Order order = Order.builder().id(orderId).userId(userId).build();
        order.addOrderProduct(OrderProduct.builder().productId(productId1).quantity(2).unitPrice(BigDecimal.TEN).build());
        order.addOrderProduct(OrderProduct.builder().productId(productId2).quantity(1).unitPrice(BigDecimal.valueOf(5)).build());

        when(orderRepository.updateStatusWithPaymentIfCurrent(orderId, OrderStatus.PENDING_CHARGE, OrderStatus.CONFIRMED, paymentId))
                .thenReturn(1);
        when(orderRepository.getReferenceById(orderId)).thenReturn(order);

        orderTransitionService.confirmOrderForPaymentCharge(event);

        verify(orderRepository).initializeShippingStatusIfNull(orderId);
        ArgumentCaptor<OrderCreated> eventCaptor = ArgumentCaptor.forClass(OrderCreated.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_CREATED), eq(KafkaTopics.ORDER_EVENTS), eventCaptor.capture());
        OrderCreated published = eventCaptor.getValue();
        assertEquals(orderId, published.orderId());
        assertEquals(userId, published.userId());
        assertEquals(2, published.items().size());
        assertTrue(published.items().stream().anyMatch(i -> i.getProductId().equals(productId1) && i.getQuantity() == 2));
        assertTrue(published.items().stream().anyMatch(i -> i.getProductId().equals(productId2) && i.getQuantity() == 1));
    }

    @Test
    void confirmOrderForPaymentCharge_whenRaceLost_doesNothing() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentSucceeded event = new PaymentSucceeded(paymentId, orderId, BigDecimal.valueOf(35));

        when(orderRepository.updateStatusWithPaymentIfCurrent(orderId, OrderStatus.PENDING_CHARGE, OrderStatus.CONFIRMED, paymentId))
                .thenReturn(0);

        orderTransitionService.confirmOrderForPaymentCharge(event);

        verify(orderRepository, never()).getReferenceById(any());
        verify(orderRepository, never()).initializeShippingStatusIfNull(any());
        verify(outboxEventService, never()).publish(any(), any(), any(), any(), any());
    }

    // ---- cancelOrderForPaymentFailure ----

    @Test
    void cancelOrderForPaymentFailure_whenCurrentStatusMatches_setsPaymentInfoCancelsOrderAndPublishesEvent() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentFailed event = new PaymentFailed(paymentId, orderId, BigDecimal.valueOf(35), "card_declined", "402");

        Order order = Order.builder().id(orderId).userId(userId).status(OrderStatus.PENDING_CHARGE).build();
        order.addOrderProduct(OrderProduct.builder().productId(productId1).quantity(2).unitPrice(BigDecimal.TEN).build());

        when(orderRepository.getReferenceById(orderId)).thenReturn(order);
        when(orderRepository.updateStatusToCancelledWithPaymentIfCurrent(orderId, OrderStatus.PENDING_CHARGE, CancelReason.PAYMENT_DECLINED, paymentId))
                .thenReturn(1);

        orderTransitionService.cancelOrderForPaymentFailure(event);

        assertEquals(paymentId, order.getPaymentId());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(CancelReason.PAYMENT_DECLINED, order.getCancelReason());

        ArgumentCaptor<NormalOrderCancelled> eventCaptor = ArgumentCaptor.forClass(NormalOrderCancelled.class);
        verify(outboxEventService).publish(eq("Order"), eq(orderId), eq(EventTypes.ORDER_NORMAL_CANCELLED), eq(KafkaTopics.ORDER_EVENTS), eventCaptor.capture());
        NormalOrderCancelled published = eventCaptor.getValue();
        assertEquals(orderId, published.orderId());
        assertEquals(userId, published.userId());
        assertEquals(CancelReason.PAYMENT_DECLINED, published.cancelReason());
        assertEquals(1, published.items().size());
        assertTrue(published.items().stream().anyMatch(i -> i.getProductId().equals(productId1) && i.getQuantity() == 2));
    }

    @Test
    void cancelOrderForPaymentFailure_whenRaceLost_doesNotMutateOrderOrPublish() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentFailed event = new PaymentFailed(paymentId, orderId, BigDecimal.valueOf(35), "card_declined", "402");

        Order order = Order.builder().id(orderId).userId(userId).status(OrderStatus.CANCELLED).build();

        when(orderRepository.getReferenceById(orderId)).thenReturn(order);
        when(orderRepository.updateStatusToCancelledWithPaymentIfCurrent(orderId, OrderStatus.PENDING_CHARGE, CancelReason.PAYMENT_DECLINED, paymentId))
        .thenReturn(0);

        orderTransitionService.cancelOrderForPaymentFailure(event);

        assertNull(order.getPaymentId());
        assertNull(order.getCardLast4());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(paymentServiceClient, never()).getPaymentMethodDetails(any(), any());
        verify(outboxEventService, never()).publish(any(), any(), any(), any(), any());
    }

    private Order reservingOrderWithProducts() {
        Order order = Order.builder().id(UUID.randomUUID()).userId(userId).status(OrderStatus.RESERVING).totalPrice(BigDecimal.valueOf(25)).build();
        order.addOrderProduct(OrderProduct.builder().productId(productId1).quantity(2).unitPrice(BigDecimal.TEN).build());
        order.addOrderProduct(OrderProduct.builder().productId(productId2).quantity(1).unitPrice(BigDecimal.valueOf(5)).build());
        return order;
    }

    private static CheckOutOrderRequest checkoutRequest(OrderItem... items) {
        return CheckOutOrderRequest.builder().orderItems(List.of(items)).paymentMethodId("pm_123").address("123 Main St").build();
    }

    private static OrderItem item(UUID productId, int quantity) {
        return OrderItem.builder().productId(productId).quantity(quantity).build();
    }
}