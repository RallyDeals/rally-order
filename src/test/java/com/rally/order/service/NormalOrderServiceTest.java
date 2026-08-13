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
import com.rally.order.model.CancelReason;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    // ---- checkoutOrder happy path ----

    @Test
    void checkoutOrder_whenProductsFoundAndInventoryReserved_preparesOrderForChargeAndReturnsResponse() {
        CheckOutOrderRequest request = checkoutRequest(item(productId1, 2), item(productId2, 1));
        CatalogLookupResponse catalogResponse = foundCatalogResponse(catalogProduct(productId1, BigDecimal.TEN), catalogProduct(productId2, BigDecimal.valueOf(20)));
        Order reservingOrder = givenReservingOrderCreated(request, catalogResponse, BigDecimal.valueOf(40));
        when(inventoryServiceClient.reserveInventory(any(InventoryReserveRequest.class))).thenReturn(
                reserveResponse(reservingOrder.getId(), reservedItem(productId1, true, 10), reservedItem(productId2, true, 10)));
        Order pendingChargeOrder = withStatus(reservingOrder, OrderStatus.PENDING_CHARGE);
        when(orderTransitionService.prepareOrderForCharge(reservingOrder, userId, request.getPaymentMethodId())).thenReturn(pendingChargeOrder);
        CheckOutOrderResponse expectedResponse = givenMappedResponse(pendingChargeOrder);

        CheckOutOrderResponse actualResponse = normalOrderService.checkoutOrder(userId, request);

        assertEquals(expectedResponse, actualResponse);
        ArgumentCaptor<InventoryReserveRequest> reserveRequestCaptor = ArgumentCaptor.forClass(InventoryReserveRequest.class);
        verify(inventoryServiceClient).reserveInventory(reserveRequestCaptor.capture());
        assertEquals(reservingOrder.getId(), reserveRequestCaptor.getValue().getOrderId());
        assertEquals(request.getOrderItems(), reserveRequestCaptor.getValue().getItems());
        verify(orderTransitionService).prepareOrderForCharge(reservingOrder, userId, request.getPaymentMethodId());
        verify(orderTransitionService, never()).cancelOrderForInventoryFailure(any(), any());
    }

    // ---- catalog lookup failures ----

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
        when(catalogServiceClient.lookup(any())).thenReturn(notFoundCatalogResponse(productId1));

        assertThrows(ProductNotFoundException.class, () -> normalOrderService.checkoutOrder(userId, request));

        verify(orderTransitionService, never()).createReservingOrder(any(), any(), any());
        verify(inventoryServiceClient, never()).reserveInventory(any(InventoryReserveRequest.class));
    }

    // ---- order creation failures (e.g. card details retrieval) ----

    @Test
    void checkoutOrder_whenCreateReservingOrderThrows_propagatesExceptionWithoutReservingInventory() {
        CheckOutOrderRequest request = checkoutRequest(item(productId1, 1));
        CatalogLookupResponse catalogResponse = foundCatalogResponse(catalogProduct(productId1, BigDecimal.TEN));
        when(catalogServiceClient.lookup(any())).thenReturn(catalogResponse);
        when(orderTransitionService.createReservingOrder(request, catalogResponse, userId))
                .thenThrow(new ServiceUnavailableException("Payment service is unavailable"));

        assertThrows(ServiceUnavailableException.class, () -> normalOrderService.checkoutOrder(userId, request));

        verify(inventoryServiceClient, never()).reserveInventory(any());
    }

    // ---- inventory reservation failures (all caught by the same catch block and mapped via reasonFrom) ----

    @ParameterizedTest(name = "{1}")
    @MethodSource("inventoryFailureScenarios")
    void checkoutOrder_whenInventoryReservationFails_cancelsOrderWithMappedReason(InventoryFailureStub stubFailure, CancelReason expectedReason) {
        CheckOutOrderRequest request = checkoutRequest(item(productId1, 1));
        CatalogLookupResponse catalogResponse = foundCatalogResponse(catalogProduct(productId1, BigDecimal.TEN));
        Order reservingOrder = givenReservingOrderCreated(request, catalogResponse, BigDecimal.TEN);
        stubFailure.applyTo(inventoryServiceClient, reservingOrder.getId(), productId1);

        assertCheckoutCancelledForInventoryFailure(request, reservingOrder, expectedReason);
    }

    private static Stream<Arguments> inventoryFailureScenarios() {
        return Stream.of(
                Arguments.of((InventoryFailureStub) (client, orderId, productId) ->
                                when(client.reserveInventory(any())).thenThrow(new ServiceUnavailableException("Inventory service is unavailable")),
                        CancelReason.INVENTORY_UNREACHABLE),
                Arguments.of((InventoryFailureStub) (client, orderId, productId) ->
                                when(client.reserveInventory(any())).thenThrow(new RuntimeException("Unexpected inventory service failure")),
                        CancelReason.SERVER_ERROR),
                Arguments.of((InventoryFailureStub) (client, orderId, productId) ->
                                when(client.reserveInventory(any())).thenReturn(reserveResponse(orderId, reservedItem(productId, false, 2))),
                        CancelReason.INSUFFICIENT_STOCK)
        );
    }

    @FunctionalInterface
    private interface InventoryFailureStub {
        void applyTo(InventoryServiceClient client, UUID orderId, UUID productId);
    }

    // ---- shared assertion for the "checkout cancelled due to inventory" scenarios ----

    private void assertCheckoutCancelledForInventoryFailure(CheckOutOrderRequest request, Order reservingOrder, CancelReason cancelReason) {
        Order cancelledOrder = cancelledOrder(reservingOrder, cancelReason);
        when(orderTransitionService.cancelOrderForInventoryFailure(reservingOrder, cancelReason)).thenReturn(cancelledOrder);
        CheckOutOrderResponse expectedResponse = givenMappedResponse(cancelledOrder);

        CheckOutOrderResponse actualResponse = normalOrderService.checkoutOrder(userId, request);

        assertEquals(expectedResponse, actualResponse);
        verify(orderTransitionService).cancelOrderForInventoryFailure(reservingOrder, cancelReason);
        verify(orderTransitionService, never()).prepareOrderForCharge(any(), any(), any());
    }

    // ---- fixtures ----

    private static CheckOutOrderRequest checkoutRequest(OrderItem... items) {
        return CheckOutOrderRequest.builder().orderItems(List.of(items)).paymentMethodId("pm_123").address("FakeAddress").build();
    }

    private static OrderItem item(UUID productId, int quantity) {
        return OrderItem.builder().productId(productId).quantity(quantity).build();
    }

    private static CatalogProduct catalogProduct(UUID productId, BigDecimal price) {
        return CatalogProduct.builder().id(productId).name("Product " + productId).imageUrl("http://img/" + productId).basePrice(price).build();
    }

    private static CatalogLookupResponse foundCatalogResponse(CatalogProduct... products) {
        return CatalogLookupResponse.builder()
                .found(stream(products).collect(toMap(CatalogProduct::getId, p -> p)))
                .notFound(List.of())
                .build();
    }

    private static CatalogLookupResponse notFoundCatalogResponse(UUID... productIds) {
        return CatalogLookupResponse.builder().found(Map.of()).notFound(List.of(productIds)).build();
    }

    private static InventoryReserveItem reservedItem(UUID productId, boolean reserved, int available) {
        return InventoryReserveItem.builder().productId(productId).reserved(reserved).available(available).build();
    }

    private static InventoryReserveResponse reserveResponse(UUID orderId, InventoryReserveItem... items) {
        return InventoryReserveResponse.builder().orderId(orderId).items(List.of(items)).build();
    }

    private Order givenReservingOrderCreated(CheckOutOrderRequest request, CatalogLookupResponse catalogResponse, BigDecimal totalPrice) {
        when(catalogServiceClient.lookup(any(CatalogLookupRequest.class))).thenReturn(catalogResponse);
        Order reservingOrder = Order.builder().id(UUID.randomUUID()).userId(userId).status(OrderStatus.RESERVING).totalPrice(totalPrice).build();
        when(orderTransitionService.createReservingOrder(request, catalogResponse, userId)).thenReturn(reservingOrder);
        return reservingOrder;
    }

    private static Order withStatus(Order source, OrderStatus status) {
        return Order.builder().id(source.getId()).userId(source.getUserId()).status(status).totalPrice(source.getTotalPrice()).build();
    }

    private static Order cancelledOrder(Order source, CancelReason cancelReason) {
        return Order.builder().id(source.getId()).userId(source.getUserId()).status(OrderStatus.CANCELLED)
                .cancelReason(cancelReason).totalPrice(source.getTotalPrice()).build();
    }

    private CheckOutOrderResponse givenMappedResponse(Order order) {
        CheckOutOrderResponse expected = CheckOutOrderResponse.builder().id(order.getId()).build();
        when(orderMapper.toCheckoutOrderResponse(order)).thenReturn(expected);
        return expected;
    }
}