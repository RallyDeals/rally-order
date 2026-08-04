package com.rally.order.service;

import com.rally.common.exceptions.domain.order.OrderNotFoundException;
import com.rally.common.exceptions.shared.BadRequestException;
import com.rally.common.exceptions.shared.UnauthorizedException;
import com.rally.order.dto.BriefOrderPageResponse;
import com.rally.order.dto.DetailedOrderResponse;
import com.rally.order.mapper.OrderMapper;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import com.rally.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
}
