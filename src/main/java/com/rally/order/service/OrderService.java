package com.rally.order.service;

import com.rally.common.exceptions.domain.order.OrderNotFoundException;
import com.rally.common.exceptions.shared.BadRequestException;
import com.rally.common.exceptions.shared.UnauthorizedException;
import com.rally.order.dto.*;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        OrderType orderType = orderRepository.findOrderTypeById(eventPayload.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found for ID: " + eventPayload.orderId()));
        if (orderType == OrderType.DEAL)
            dealOrderService.handlePaymentFailed(eventPayload);
        else
            normalOrderService.handlePaymentFailed(eventPayload);
    }

    public BriefOrderPageResponse getMyOrders(UUID userId, List<String> statusParams, String typeParam, int page, int limit) {
        if (page < 1)
            throw new BadRequestException("page must be >= 1");
        if (limit < 1 || limit > MAX_LIMIT)
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);

        OrderType orderTypeParam = parseOrderType(typeParam);
        List<BuyerFiltrationOrderStatus> statuses = parseBuyerFiltrationStatuses(statusParams);

        Specification<Order> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);

        if (orderTypeParam != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("orderType"), orderTypeParam));
        }

        if (statuses != null && !statuses.isEmpty()) {
            Set<OrderStatus> orderStatuses = EnumSet.noneOf(OrderStatus.class);
            Set<ShippingStatus> shippingStatuses = EnumSet.noneOf(ShippingStatus.class);
            for (BuyerFiltrationOrderStatus statusParam : statuses) {
                Map<String, List<String>> parsed = parseBuyerFiltrationStatus(statusParam);
                parsed.get("status").forEach(s -> orderStatuses.add(OrderStatus.valueOf(s)));
                parsed.get("shippingStatus").forEach(s -> shippingStatuses.add(ShippingStatus.valueOf(s)));
            }

            Specification<Order> statusSpec = orderStatuses.isEmpty() ? null :
                    (root, query, cb) -> root.get("status").in(orderStatuses);
            Specification<Order> shippingSpec = shippingStatuses.isEmpty() ? null :
                    (root, query, cb) -> root.get("shippingStatus").in(shippingStatuses);

            Specification<Order> filterSpec;
            if (statusSpec != null && shippingSpec != null) {
                filterSpec = statusSpec.or(shippingSpec);
            } else if (statusSpec != null) {
                filterSpec = statusSpec;
            } else {
                filterSpec = shippingSpec;
            }
            spec = spec.and(filterSpec);
        }

        Page<Order> result = orderRepository.findAll(spec,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        return BriefOrderPageResponse.builder()
                .orders(mapper.toBriefOrders(result.getContent()))
                .page(page)
                .limit(limit)
                .total(result.getTotalElements())
                .build();

    }

    public BuyerOrdersAnalytics getMyOrdersStatistics(UUID userId) {
        Object[] row = orderRepository.getBuyerOrdersAnalyticsRaw(userId).get(0);

        return BuyerOrdersAnalytics.builder()
                .deliveredOrders(toInt(row[0]))
                .cancelledOrders(toInt(row[1]))
                .pendingDelivery(toInt(row[2]))
                .pendingPayment(toInt(row[3]))
                .build();
    }

    private Integer toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private OrderType parseOrderType(String typeParam) {
        if (typeParam == null || typeParam.isBlank())
            return null;
        try {
            return OrderType.valueOf(typeParam.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid type filter value: " + typeParam +
                    ". Expected one of " + Arrays.toString(OrderType.values()));
        }
    }

    private List<BuyerFiltrationOrderStatus> parseBuyerFiltrationStatuses(List<String> statusParams) {
        if (statusParams == null || statusParams.isEmpty())
            return null;
        return statusParams.stream().map(statusParam -> {
            try {
                return BuyerFiltrationOrderStatus.valueOf(statusParam.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status filter value: " + statusParam +
                        ". Expected one of " + Arrays.toString(BuyerFiltrationOrderStatus.values()));
            }
        }).toList();
    }

    private Map<String, List<String>> parseBuyerFiltrationStatus(BuyerFiltrationOrderStatus statusParam) {
        List<String> o = switch (statusParam) {
            case PENDING_PAYMENT ->
                    List.of(OrderStatus.PENDING_CAPTURE.name(), OrderStatus.PENDING_CHARGE.name(), OrderStatus.PENDING_AUTHORIZATION.name(), OrderStatus.PENDING_VOID.name());
            case CANCELLED -> List.of(OrderStatus.CANCELLED.name());
            default -> List.of();
        };
        List<String> s = switch (statusParam) {
            case PENDING_DELIVERY -> List.of(ShippingStatus.SHIPPING.name(), ShippingStatus.PROCESSING.name());
            case DELIVERED -> List.of(ShippingStatus.DELIVERED.name());
            default -> List.of();
        };
        return new HashMap<>() {{
            put("status", o);
            put("shippingStatus", s);
        }};
    }

    public DetailedOrderResponse getOrderDetails(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found for ID: " + orderId));
        if (!order.getUserId().equals(userId)) {
            throw new UnauthorizedException("User " + userId + " is not authorized to access order " + orderId);
        }
        return mapper.toDetailedOrderResponse(order);
    }

    public BriefSellerOrderPageResponse getSellerOrders(UUID callerId, String role, UUID sellerId, String
            statusParam, String startDate, String search, int page, int limit) {
        requireSeller(callerId, role, sellerId);
        if (page < 1)
            throw new BadRequestException("page must be >= 1");
        if (limit < 1 || limit > MAX_LIMIT)
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);

        CompactedOrderStatus status = parseCompactedStatus(statusParam);

        OffsetDateTime parsedStartDate = parseStartDate(startDate);
        String normalizedSearch = normalizeSearch(search);

        Page<Order> result = orderRepository.findOrdersBySellerIdAndFilters(sellerId, statusesFor(status), shippingStatusFor(status), parsedStartDate, normalizedSearch,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<UUID> orderIds = result.getContent().stream().map(Order::getId).toList();
        Map<UUID, List<OrderProductResponse>> itemsByOrderId = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findByIdsWithSellerItems(orderIds, sellerId).stream()
                .collect(Collectors.toMap(Order::getId, order -> mapper.toOrderProductResponses(order.getOrderProducts())));

        List<BriefSellerOrderResponse> orders = result.getContent().stream()
                .map(order -> mapper.toBriefSellerOrderResponse(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
                .toList();
        return BriefSellerOrderPageResponse.builder()
                .orders(orders)
                .page(page)
                .limit(limit)
                .total(result.getTotalElements())
                .build();
    }

    public DetailedSellerOrderResponse getSellerOrderDetails(UUID callerId, String role, UUID sellerId, UUID
            orderId) {
        requireSeller(callerId, role, sellerId);
        Order order = orderRepository.findByIdAndSellerId(orderId, sellerId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found for ID: " + orderId));
        return mapper.toDetailedSellerOrderResponse(order, mapper.toOrderProductResponses(order.getOrderProducts()));
    }

    public SellerOrdersAnalytics getSellerOrdersAnalytics(UUID callerId, String role, String startDate, UUID
            sellerId) {
        requireSeller(callerId, role, sellerId);
        OffsetDateTime parsedStartDate = parseStartDate(startDate);
        List<Object[]> rows = orderRepository.getSellerOrdersAnalyticsRaw(sellerId, parsedStartDate);
        Object[] row = rows.get(0);

        long totalOrders = (long) row[0];
        long pendingOrders = (long) row[1];
        long deliveredOrders = (long) row[2];
        BigDecimal revenue = (BigDecimal) row[3];

        return SellerOrdersAnalytics.builder()
                .totalOrders((int) totalOrders)
                .pendingOrders((int) pendingOrders)
                .deliveredOrders((int) deliveredOrders)
                .revenue(revenue)
                .build();
    }

    private OffsetDateTime parseStartDate(String startDate) {
        if (startDate == null || startDate.isBlank()) {
            return OffsetDateTime.now().minusMonths(1);
        }

        try {
            return LocalDate.parse(startDate).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(startDate);
            } catch (DateTimeParseException e) {
                throw new BadRequestException("Invalid startDate filter value: " + startDate +
                        ". Expected format is yyyy-MM-dd or ISO-8601 datetime.");
            }
        }
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private void requireSeller(UUID callerId, String role, UUID sellerId) {
        if (!"SELLER".equalsIgnoreCase(role)) {
            throw new UnauthorizedException("SELLER role required");
        }
        if (!sellerId.equals(callerId)) {
            throw new UnauthorizedException("User " + callerId + " is not authorized to access seller " + sellerId + "'s orders");
        }
    }

    private CompactedOrderStatus parseCompactedStatus(String compactedStatus) {
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

}
