package com.rally.order.service;

import com.rally.common.exceptions.domain.order.OrderNotFoundException;
import com.rally.common.exceptions.shared.BadRequestException;
import com.rally.common.exceptions.shared.UnauthorizedException;
import com.rally.order.dto.DetailedOrderResponse;
import com.rally.order.mapper.OrderMapper;
import com.rally.order.model.Order;
import com.rally.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private NormalOrderService normalOrderService;
    @Mock
    private DealOrderService dealOrderService;
    @Mock
    private OrderMapper mapper;

    private OrderService orderService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, normalOrderService, dealOrderService, mapper);
        userId = UUID.randomUUID();
    }

    // ---- getMyOrders ----
    @Test
    void getMyOrders_pageLessThanOne_throwsBadRequestException() {
        assertThrows(BadRequestException.class, () -> orderService.getMyOrders(userId, null, null, 0, 20));
        verifyNoInteractions(orderRepository);
    }

    @Test
    void getMyOrders_limitZero_throwsBadRequestException() {
        assertThrows(BadRequestException.class, () -> orderService.getMyOrders(userId, null, null, 1, 0));
        verifyNoInteractions(orderRepository);
    }

    @Test
    void getMyOrders_limitOverMax_throwsBadRequestException() {
        assertThrows(BadRequestException.class, () -> orderService.getMyOrders(userId, null, null, 1, 101));
        verifyNoInteractions(orderRepository);
    }

    // ---- getOrderDetails ----

    @Test
    void getOrderDetails_whenFoundAndOwnedByUser_returnsDetailedResponse() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).userId(userId).build();
        DetailedOrderResponse expected = DetailedOrderResponse.builder().orderId(orderId).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(mapper.toDetailedOrderResponse(order)).thenReturn(expected);

        DetailedOrderResponse result = orderService.getOrderDetails(userId, orderId);

        assertSame(expected, result);
    }

    @Test
    void getOrderDetails_whenNotFound_throwsOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrderDetails(userId, orderId));
    }

    @Test
    void getOrderDetails_whenNotOwnedByUser_throwsUnauthorizedException() {
        UUID orderId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).userId(otherUserId).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(UnauthorizedException.class, () -> orderService.getOrderDetails(userId, orderId));
    }
}
