package com.rally.order.service;

import com.rally.common.exceptions.domain.catalog.ProductNotFoundException;
import com.rally.common.exceptions.domain.inventory.InsufficientStockException;
import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.CatalogServiceClient;
import com.rally.order.client.InventoryServiceClient;
import com.rally.order.client.dto.CatalogLookupRequest;
import com.rally.order.client.dto.CatalogLookupResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NormalOrderService {
    private final CatalogServiceClient catalogServiceClient;
    private final InventoryServiceClient inventoryServiceClient;
    private final NormalOrderTransitionService orderTransitionService;
    private final OrderMapper orderMapper;

    public CheckOutOrderResponse checkoutOrder(UUID userId, CheckOutOrderRequest orderRequest) {
        List<UUID> productIds = orderRequest.getOrderItems().stream().map(OrderItem::getProductId).toList();
        // Validate Order Items and retrieve them from Catalog
        CatalogLookupResponse catalogLookupResponse = lookupProducts(productIds);
        // Build order with status RESERVING
        Order order = orderTransitionService.createReservingOrder(orderRequest, catalogLookupResponse, userId);
        // Reserve quantity
        try {
            reserveProductQuantities(orderRequest.getOrderItems(), order.getId());
        } catch (Exception e) {
            order = orderTransitionService.cancelOrderForInventoryFailure(order, reasonFrom(e));
            return orderMapper.toCheckoutOrderResponse(order);
        }
        // Update status and fire payment charge event
        order = orderTransitionService.prepareOrderForCharge(order, userId, orderRequest.getPaymentMethodId());
        // return
        return orderMapper.toCheckoutOrderResponse(order);
    }

    public void handlePaymentCharged(PaymentSucceeded eventPayload) {
        orderTransitionService.confirmOrderForPaymentCharge(eventPayload);
    }

    public void handlePaymentFailed(PaymentFailed eventPayload) {
        orderTransitionService.cancelOrderForPaymentFailure(eventPayload);
    }

    public void expireStuckReservation(Order order) {
        orderTransitionService.cancelOrderForInventoryFailure(order, CancelReason.RESERVATION_INCOMPLETE);
    }

    public void expireStuckCharge(Order order){
        orderTransitionService.cancelOrderForPaymentTimeout(order);
    }

    private CatalogLookupResponse lookupProducts(List<UUID> productIds) {
        CatalogLookupResponse response = catalogServiceClient.lookup(CatalogLookupRequest.builder().productIds(productIds).build());
        if (!response.getNotFound().isEmpty()) {
            throw new ProductNotFoundException(response.getNotFound().get(0));
        }
        return response;
    }

    private void reserveProductQuantities(List<OrderItem> orderItems, UUID orderId) {
        InventoryReserveResponse reserveResponse = inventoryServiceClient.reserveInventory(
                InventoryReserveRequest.builder()
                        .orderId(orderId)
                        .items(orderItems)
                        .build()
        );

        Map<UUID, Integer> qtyByProduct = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
        List<InsufficientStockException.Shortage> shortages = reserveResponse.getItems().stream()
                .filter(i -> !i.isReserved())
                .map(i -> new InsufficientStockException.Shortage(
                        i.getProductId(),
                        qtyByProduct.getOrDefault(i.getProductId(), 0),
                        i.getAvailable()))
                .toList();
        if (!shortages.isEmpty()) {
            throw new InsufficientStockException(shortages);
        }
    }

    private CancelReason reasonFrom(Exception e) {
        return (e instanceof InsufficientStockException) ? CancelReason.INSUFFICIENT_STOCK :
                (e instanceof ServiceUnavailableException) ? CancelReason.INVENTORY_UNREACHABLE : CancelReason.SERVER_ERROR;
    }
}
