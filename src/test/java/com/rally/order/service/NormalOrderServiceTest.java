package com.rally.order.service;

import com.rally.common.exceptions.domain.catalog.ProductNotFoundException;
import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.CatalogServiceClient;
import com.rally.order.client.InventoryServiceClient;
import com.rally.order.client.dto.CatalogLookupRequest;
import com.rally.order.client.dto.CatalogLookupResponse;
import com.rally.order.client.dto.CatalogProduct;
import com.rally.order.client.dto.InventoryReserveItem;
import com.rally.order.client.dto.InventoryReserveRequest;
import com.rally.order.client.dto.InventoryReserveResponse;
import com.rally.order.dto.CheckOutOrderRequest;
import com.rally.order.dto.CheckOutOrderResponse;
import com.rally.order.dto.OrderItem;
import com.rally.order.mapper.OrderMapper;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.messaging.event.inbound.payment.PaymentSucceeded;
import com.rally.order.model.CancelReason;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NormalOrderServiceTest {

    @Mock
    private CatalogServiceClient catalogServiceClient;
    @Mock
    private InventoryServiceClient inventoryServiceClient;
    @Mock
    private NormalOrderTransitionService orderTransitionService;
    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private NormalOrderService normalOrderService;

    private UUID userId;
    private UUID productId1;
    private UUID productId2;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId1 = UUID.randomUUID();
        productId2 = UUID.randomUUID();
    }

    @Test
    void checkoutOrder_whenProductsFoundAndInventoryReserved_preparesOrderForChargeAndReturnsResponse() {
        CheckOutOrderRequest request = checkoutRequest(
                item(productId1, 2),
                item(productId2, 1)
        );
        CatalogLookupResponse catalogResponse = CatalogLookupResponse.builder()
                .found(Map.of(productId1, catalogProduct(productId1, BigDecimal.TEN), productId2, catalogProduct(productId2, BigDecimal.valueOf(20))))
                .notFound(List.of())
                .build();
        when(catalogServiceClient.lookup(any(CatalogLookupRequest.class))).thenReturn(catalogResponse);

        Order reservingOrder = Order.builder()
                .id(UUID.randomUUID())
                .orderType(OrderType.NORMAL)
                .userId(userId)
                .status(OrderStatus.RESERVING)
                .totalPrice(BigDecimal.valueOf(40))
                .build();
        when(orderTransitionService.createReservingOrder(request, catalogResponse, userId)).thenReturn(reservingOrder);

        when(inventoryServiceClient.reserveInventory(any(InventoryReserveRequest.class)))
                .thenReturn(InventoryReserveResponse.builder()
                        .orderId(reservingOrder.getId())
                        .items(List.of(
                                InventoryReserveItem.builder().productId(productId1).reserved(true).available(10).build(),
                                InventoryReserveItem.builder().productId(productId2).reserved(true).available(10).build()
                        ))
                        .build());

        Order pendingChargeOrder = Order.builder()
                .id(reservingOrder.getId())
                .userId(userId)
                .status(OrderStatus.PENDING_CHARGE)
                .totalPrice(reservingOrder.getTotalPrice())
                .build();
        when(orderTransitionService.prepareOrderForCharge(reservingOrder, userId, request.getPaymentMethodId()))
                .thenReturn(pendingChargeOrder);

        CheckOutOrderResponse expectedResponse = CheckOutOrderResponse.builder().id(reservingOrder.getId()).build();
        when(orderMapper.toCheckoutOrderResponse(pendingChargeOrder)).thenReturn(expectedResponse);

        CheckOutOrderResponse actualResponse = normalOrderService.checkoutOrder(userId, request);

        assertEquals(expectedResponse, actualResponse);
        ArgumentCaptor<InventoryReserveRequest> reserveRequestCaptor = ArgumentCaptor.forClass(InventoryReserveRequest.class);
        verify(inventoryServiceClient, times(1)).reserveInventory(reserveRequestCaptor.capture());
        InventoryReserveRequest reserveRequest = reserveRequestCaptor.getValue();
        assertEquals(reservingOrder.getId(), reserveRequest.getOrderId());
        assertTrue(reserveRequest.getItems().stream().anyMatch(i -> i.getProductId().equals(productId1) && i.getQuantity() == 2));
        assertTrue(reserveRequest.getItems().stream().anyMatch(i -> i.getProductId().equals(productId2) && i.getQuantity() == 1));
        verify(orderTransitionService).prepareOrderForCharge(reservingOrder, userId, request.getPaymentMethodId());
        verify(orderTransitionService, never()).cancelOrderForInventoryFailure(any(), any());
    }

    @Test
    void handlePaymentCharged_delegatesToOrderTransitionService() {
        PaymentSucceeded event = new PaymentSucceeded(UUID.randomUUID(), "pi_123", UUID.randomUUID(), BigDecimal.valueOf(40));

        normalOrderService.handlePaymentCharged(event);

        verify(orderTransitionService).confirmOrderForPaymentCharge(event);
    }

    @Test
    void handlePaymentFailed_delegatesToOrderTransitionService() {
        PaymentFailed event = new PaymentFailed(UUID.randomUUID(), "pi_123", UUID.randomUUID(), BigDecimal.valueOf(40), "card_declined", "402");

        normalOrderService.handlePaymentFailed(event);

        verify(orderTransitionService).cancelOrderForPaymentFailure(event);
    }

    @Test
    void expireStuckReservation_delegatesToOrderTransitionServiceWithReservationIncompleteReason() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.RESERVING)
                .totalPrice(BigDecimal.TEN)
                .build();

        normalOrderService.expireStuckReservation(order);

        verify(orderTransitionService).cancelOrderForInventoryFailure(order, CancelReason.RESERVATION_INCOMPLETE);
    }

    // ---- Catalog unavailable / products not found ----

    @Test
    void checkoutOrder_whenCatalogServiceUnavailable_propagatesExceptionWithoutCreatingOrder() {
        CheckOutOrderRequest request = checkoutRequest(item(productId1, 1));
        when(catalogServiceClient.lookup(any())).thenThrow(new ServiceUnavailableException("Catalog service is unavailable"));

        assertThrows(ServiceUnavailableException.class, () -> normalOrderService.checkoutOrder(userId, request));

        verify(orderTransitionService, never()).createReservingOrder(any(), any(), any());
        verify(inventoryServiceClient, never()).reserveInventory(any(InventoryReserveRequest.class));
    }

    @Test
    void checkoutOrder_whenProductNotFoundInCatalog_throwsWithoutCreatingOrder() {
        CheckOutOrderRequest request = checkoutRequest(item(productId1, 1));
        CatalogLookupResponse catalogResponse = CatalogLookupResponse.builder()
                .found(Map.of())
                .notFound(List.of(productId1))
                .build();
        when(catalogServiceClient.lookup(any())).thenReturn(catalogResponse);

        assertThrows(ProductNotFoundException.class, () -> normalOrderService.checkoutOrder(userId, request));

        verify(orderTransitionService, never()).createReservingOrder(any(), any(), any());
        verify(inventoryServiceClient, never()).reserveInventory(any(InventoryReserveRequest.class));
    }

    // ---- Inventory unavailable / insufficient stock ----

    @Test
    void checkoutOrder_whenInventoryServiceUnavailable_cancelsOrderWithInventoryUnreachableReason() {
        CheckOutOrderRequest request = checkoutRequest(item(productId1, 1));
        CatalogLookupResponse catalogResponse = CatalogLookupResponse.builder()
                .found(Map.of(productId1, catalogProduct(productId1, BigDecimal.TEN)))
                .notFound(List.of())
                .build();
        when(catalogServiceClient.lookup(any())).thenReturn(catalogResponse);

        Order reservingOrder = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.RESERVING)
                .totalPrice(BigDecimal.TEN)
                .build();
        when(orderTransitionService.createReservingOrder(request, catalogResponse, userId)).thenReturn(reservingOrder);

        when(inventoryServiceClient.reserveInventory(any(InventoryReserveRequest.class)))
                .thenThrow(new ServiceUnavailableException("Inventory service is unavailable"));

        Order cancelledOrder = Order.builder()
                .id(reservingOrder.getId())
                .userId(userId)
                .status(OrderStatus.CANCELLED)
                .cancelReason(CancelReason.INVENTORY_UNREACHABLE)
                .totalPrice(reservingOrder.getTotalPrice())
                .build();
        when(orderTransitionService.cancelOrderForInventoryFailure(reservingOrder, CancelReason.INVENTORY_UNREACHABLE))
                .thenReturn(cancelledOrder);

        CheckOutOrderResponse expectedResponse = CheckOutOrderResponse.builder().id(reservingOrder.getId()).build();
        when(orderMapper.toCheckoutOrderResponse(cancelledOrder)).thenReturn(expectedResponse);

        CheckOutOrderResponse actualResponse = normalOrderService.checkoutOrder(userId, request);

        assertEquals(expectedResponse, actualResponse);
        verify(orderTransitionService).cancelOrderForInventoryFailure(reservingOrder, CancelReason.INVENTORY_UNREACHABLE);
        verify(orderTransitionService, never()).prepareOrderForCharge(any(), any(), any());
    }

    @Test
    void checkoutOrder_whenInventoryServiceThrowsUnexpectedException_cancelsOrderWithServerErrorReason() {
        CheckOutOrderRequest request = checkoutRequest(item(productId1, 1));
        CatalogLookupResponse catalogResponse = CatalogLookupResponse.builder()
                .found(Map.of(productId1, catalogProduct(productId1, BigDecimal.TEN)))
                .notFound(List.of())
                .build();
        when(catalogServiceClient.lookup(any())).thenReturn(catalogResponse);

        Order reservingOrder = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.RESERVING)
                .totalPrice(BigDecimal.TEN)
                .build();
        when(orderTransitionService.createReservingOrder(request, catalogResponse, userId)).thenReturn(reservingOrder);

        when(inventoryServiceClient.reserveInventory(any(InventoryReserveRequest.class)))
                .thenThrow(new RuntimeException("Unexpected inventory service failure"));

        Order cancelledOrder = Order.builder()
                .id(reservingOrder.getId())
                .userId(userId)
                .status(OrderStatus.CANCELLED)
                .cancelReason(CancelReason.SERVER_ERROR)
                .totalPrice(reservingOrder.getTotalPrice())
                .build();
        when(orderTransitionService.cancelOrderForInventoryFailure(reservingOrder, CancelReason.SERVER_ERROR))
                .thenReturn(cancelledOrder);

        CheckOutOrderResponse expectedResponse = CheckOutOrderResponse.builder().id(reservingOrder.getId()).build();
        when(orderMapper.toCheckoutOrderResponse(cancelledOrder)).thenReturn(expectedResponse);

        CheckOutOrderResponse actualResponse = normalOrderService.checkoutOrder(userId, request);

        assertEquals(expectedResponse, actualResponse);
        verify(orderTransitionService).cancelOrderForInventoryFailure(reservingOrder, CancelReason.SERVER_ERROR);
        verify(orderTransitionService, never()).prepareOrderForCharge(any(), any(), any());
    }

    @Test
    void checkoutOrder_whenStockInsufficient_cancelsOrderWithInsufficientStockReason() {
        CheckOutOrderRequest request = checkoutRequest(item(productId1, 5));
        CatalogLookupResponse catalogResponse = CatalogLookupResponse.builder()
                .found(Map.of(productId1, catalogProduct(productId1, BigDecimal.TEN)))
                .notFound(List.of())
                .build();
        when(catalogServiceClient.lookup(any())).thenReturn(catalogResponse);

        Order reservingOrder = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.RESERVING)
                .totalPrice(BigDecimal.valueOf(50))
                .build();
        when(orderTransitionService.createReservingOrder(request, catalogResponse, userId)).thenReturn(reservingOrder);

        when(inventoryServiceClient.reserveInventory(any(InventoryReserveRequest.class)))
                .thenReturn(InventoryReserveResponse.builder()
                        .orderId(reservingOrder.getId())
                        .items(List.of(InventoryReserveItem.builder().productId(productId1).reserved(false).available(2).build()))
                        .build());

        Order cancelledOrder = Order.builder()
                .id(reservingOrder.getId())
                .userId(userId)
                .status(OrderStatus.CANCELLED)
                .cancelReason(CancelReason.INSUFFICIENT_STOCK)
                .totalPrice(reservingOrder.getTotalPrice())
                .build();
        when(orderTransitionService.cancelOrderForInventoryFailure(reservingOrder, CancelReason.INSUFFICIENT_STOCK))
                .thenReturn(cancelledOrder);

        CheckOutOrderResponse expectedResponse = CheckOutOrderResponse.builder().id(reservingOrder.getId()).build();
        when(orderMapper.toCheckoutOrderResponse(cancelledOrder)).thenReturn(expectedResponse);

        CheckOutOrderResponse actualResponse = normalOrderService.checkoutOrder(userId, request);

        assertEquals(expectedResponse, actualResponse);
        verify(orderTransitionService).cancelOrderForInventoryFailure(reservingOrder, CancelReason.INSUFFICIENT_STOCK);
        verify(orderTransitionService, never()).prepareOrderForCharge(any(), any(), any());
    }

    @Test
    void checkoutOrder_whenOneOfMultipleItemsInsufficientInBatchResponse_cancelsOrderWithInsufficientStockReason() {
        CheckOutOrderRequest request = checkoutRequest(
                item(productId1, 2),
                item(productId2, 5)
        );
        CatalogLookupResponse catalogResponse = CatalogLookupResponse.builder()
                .found(Map.of(productId1, catalogProduct(productId1, BigDecimal.TEN), productId2, catalogProduct(productId2, BigDecimal.valueOf(20))))
                .notFound(List.of())
                .build();
        when(catalogServiceClient.lookup(any())).thenReturn(catalogResponse);

        Order reservingOrder = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.RESERVING)
                .totalPrice(BigDecimal.valueOf(120))
                .build();
        when(orderTransitionService.createReservingOrder(request, catalogResponse, userId)).thenReturn(reservingOrder);

        when(inventoryServiceClient.reserveInventory(any(InventoryReserveRequest.class)))
                .thenReturn(InventoryReserveResponse.builder()
                        .orderId(reservingOrder.getId())
                        .items(List.of(
                                InventoryReserveItem.builder().productId(productId1).reserved(true).available(10).build(),
                                InventoryReserveItem.builder().productId(productId2).reserved(false).available(3).build()
                        ))
                        .build());

        Order cancelledOrder = Order.builder()
                .id(reservingOrder.getId())
                .userId(userId)
                .status(OrderStatus.CANCELLED)
                .cancelReason(CancelReason.INSUFFICIENT_STOCK)
                .totalPrice(reservingOrder.getTotalPrice())
                .build();
        when(orderTransitionService.cancelOrderForInventoryFailure(reservingOrder, CancelReason.INSUFFICIENT_STOCK))
                .thenReturn(cancelledOrder);

        CheckOutOrderResponse expectedResponse = CheckOutOrderResponse.builder().id(reservingOrder.getId()).build();
        when(orderMapper.toCheckoutOrderResponse(cancelledOrder)).thenReturn(expectedResponse);

        CheckOutOrderResponse actualResponse = normalOrderService.checkoutOrder(userId, request);

        assertEquals(expectedResponse, actualResponse);
        verify(inventoryServiceClient, times(1)).reserveInventory(any(InventoryReserveRequest.class));
        verify(orderTransitionService).cancelOrderForInventoryFailure(reservingOrder, CancelReason.INSUFFICIENT_STOCK);
        verify(orderTransitionService, never()).prepareOrderForCharge(any(), any(), any());
    }

    private static CheckOutOrderRequest checkoutRequest(OrderItem... items) {
        return CheckOutOrderRequest.builder().orderItems(List.of(items)).paymentMethodId("pm_123").build();
    }

    private static OrderItem item(UUID productId, int quantity) {
        return OrderItem.builder().productId(productId).quantity(quantity).build();
    }

    private static CatalogProduct catalogProduct(UUID productId, BigDecimal price) {
        return CatalogProduct.builder().productId(productId).name("Product " + productId).imageUrl("http://img/" + productId).price(price).build();
    }
}
