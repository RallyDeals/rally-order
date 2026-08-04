package com.rally.order.service;

import com.rally.common.exceptions.domain.order.OrderNotFoundException;
import com.rally.common.exceptions.shared.UnauthorizedException;
import com.rally.order.dto.BriefOrderResponse;
import com.rally.order.dto.DetailedOrderResponse;
import com.rally.order.mapper.OrderMapper;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.model.Order;
import com.rally.order.model.OrderType;
import com.rally.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {
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

    public List<BriefOrderResponse> getMyOrders(UUID userId){
        return mapper.toBriefOrders(orderRepository.findByUserId(userId));
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
