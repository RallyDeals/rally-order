package com.rally.order.service;

import com.rally.common.exceptions.domain.order.OrderNotFoundException;
import com.rally.common.exceptions.shared.BadRequestException;
import com.rally.common.exceptions.shared.UnauthorizedException;
import com.rally.order.dto.BriefOrderPageResponse;
import com.rally.order.dto.BriefSellerOrderItemResponse;
import com.rally.order.dto.BriefSellerOrderPageResponse;
import com.rally.order.dto.BriefSellerOrderResponse;
import com.rally.order.dto.CompactedOrderStatus;
import com.rally.order.dto.DetailedOrderResponse;
import com.rally.order.dto.DetailedSellerOrderResponse;
import com.rally.order.mapper.OrderMapper;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import com.rally.order.model.ShippingStatus;
import com.rally.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final int MAX_LIMIT = 100;

    private final OrderRepository orderRepository;
    private final NormalOrderService normalOrderService;
    private final DealOrderService dealOrderService;
    private final OrderMapper mapper;

    public void handlePaymentFailed(PaymentFailed eventPayload) {
        Order order = orderRepository.findById(eventPayload.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId()));
        if(order.getOrderType() == OrderType.DEAL)
            dealOrderService.handlePaymentFailed(eventPayload);
        else
            normalOrderService.handlePaymentFailed(eventPayload);
    }

    public BriefOrderPageResponse getMyOrders(UUID userId, String statusParam, String orderTypeParam, int page, int limit){
        if (page < 1)
            throw new BadRequestException("page must be >= 1");
        if (limit < 1 || limit > MAX_LIMIT)
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);

        OrderStatus status = parseStatus(statusParam);
        OrderType orderType = parseOrderType(orderTypeParam);

        Page<Order> result = orderRepository.findByUserIdAndFilters(userId, status, orderType,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        return BriefOrderPageResponse.builder()
                .orders(mapper.toBriefOrders(result.getContent()))
                .page(page)
                .limit(limit)
                .total(result.getTotalElements())
                .build();
    }

    private OrderStatus parseStatus(String statusParam) {
        if (statusParam == null)
            return null;
        try {
            return OrderStatus.valueOf(statusParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status filter value: " + statusParam);
        }
    }

    private OrderType parseOrderType(String orderTypeParam) {
        if (orderTypeParam == null)
            return null;
        try {
            return OrderType.valueOf(orderTypeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid orderType filter value: " + orderTypeParam);
        }
    }

    public DetailedOrderResponse getOrderDetails(UUID userId, UUID orderId){
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found for ID: " + orderId));
        if(!order.getUserId().equals(userId)){
            throw new UnauthorizedException("User " + userId + " is not authorized to access order " + orderId);
        }
        return mapper.toDetailedOrderResponse(order);
    }

    public BriefSellerOrderPageResponse getSellerOrders(UUID sellerId, String statusParam, int page, int limit){
        if (page < 1)
            throw new BadRequestException("page must be >= 1");
        if (limit < 1 || limit > MAX_LIMIT)
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);

        CompactedOrderStatus status = parseCompactedStatus(statusParam);

        Page<Order> result = orderRepository.findOrdersBySellerIdAndFilters(sellerId, statusesFor(status), shippingStatusFor(status),
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<UUID> orderIds = result.getContent().stream().map(Order::getId).toList();
        Map<UUID, List<BriefSellerOrderItemResponse>> itemsByOrderId = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findByIdsWithSellerItems(orderIds, sellerId).stream()
                        .collect(Collectors.toMap(Order::getId, this::toSellerOrderItems));

        List<BriefSellerOrderResponse> orders = result.getContent().stream()
                .map(order -> toBriefSellerOrderResponse(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
                .toList();

        return BriefSellerOrderPageResponse.builder()
                .orders(orders)
                .page(page)
                .limit(limit)
                .total(result.getTotalElements())
                .build();
    }

    public DetailedSellerOrderResponse getSellerOrderDetails(UUID sellerId, UUID orderId){
        Order order = orderRepository.findByIdAndSellerId(orderId, sellerId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found for ID: " + orderId));
        return toDetailedSellerOrderResponse(order);
    }

    private CompactedOrderStatus parseCompactedStatus(String compactedStatus){
        if (compactedStatus == null)
            return null;
        try {
            return CompactedOrderStatus.valueOf(compactedStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status filter value: " + compactedStatus);
        }
    }

    private List<OrderStatus> statusesFor(CompactedOrderStatus status) {
        if (status == null)
            return null;
        return switch (status) {
            case PENDING -> List.of(OrderStatus.RESERVING, OrderStatus.PENDING_CHARGE);
            case CANCELLED -> List.of(OrderStatus.CANCELLED);
            case PROCESSING, SHIPPING, DELIVERED -> List.of(OrderStatus.CONFIRMED);
        };
    }

    private ShippingStatus shippingStatusFor(CompactedOrderStatus status) {
        if (status == null)
            return null;
        return switch (status) {
            case PROCESSING -> ShippingStatus.PROCESSING;
            case SHIPPING -> ShippingStatus.SHIPPING;
            case DELIVERED -> ShippingStatus.DELIVERED;
            default -> null;
        };
    }

    private BriefSellerOrderResponse toBriefSellerOrderResponse(Order order, List<BriefSellerOrderItemResponse> items) {
        return BriefSellerOrderResponse.builder()
                .orderId(order.getId())
                .status(toCompactedStatus(order))
                .createdAt(order.getCreatedAt())
                .items(items)
                .build();
    }

    private DetailedSellerOrderResponse toDetailedSellerOrderResponse(Order order) {
        return DetailedSellerOrderResponse.builder()
                .orderId(order.getId())
                .status(toCompactedStatus(order))
                .cancelReason(order.getCancelReason())
                .address(order.getAddress())
                .createdAt(order.getCreatedAt())
                .items(toSellerOrderItems(order))
                .build();
    }

    private List<BriefSellerOrderItemResponse> toSellerOrderItems(Order order) {
        return order.getOrderProducts().stream()
                .map(op -> BriefSellerOrderItemResponse.builder()
                        .productId(op.getProductId())
                        .productName(op.getProductName())
                        .productImageUrl(op.getProductImageUrl())
                        .quantity(op.getQuantity())
                        .unitPrice(op.getUnitPrice())
                        .build())
                .toList();
    }

    private CompactedOrderStatus toCompactedStatus(Order order) {
        return switch (order.getStatus()) {
            case CANCELLED -> CompactedOrderStatus.CANCELLED;
            case CONFIRMED -> order.getShippingStatus() != null
                    ? CompactedOrderStatus.valueOf(order.getShippingStatus().name())
                    : CompactedOrderStatus.PENDING;
            default -> CompactedOrderStatus.PENDING;
        };
    }
}
