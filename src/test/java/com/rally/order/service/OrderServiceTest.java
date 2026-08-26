package com.rally.order.service;

import com.rally.common.exceptions.domain.order.OrderNotFoundException;
import com.rally.common.exceptions.shared.BadRequestException;
import com.rally.common.exceptions.shared.UnauthorizedException;
import com.rally.order.dto.DetailedOrderResponse;
import com.rally.order.mapper.OrderMapper;
import com.rally.order.model.Order;
import com.rally.order.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    @Test
    void getMyOrders_withStatusFilter_callsRepositoryWithSpecification() {
        when(orderRepository.findAll(ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        orderService.getMyOrders(userId, List.of("PENDING_PAYMENT"), null, 1, 20);

        verify(orderRepository).findAll(ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class));
    }

    @Test
    void getMyOrders_withTypeOnly_doesNotCrashAndSkipsStatusFilters() {
        when(orderRepository.findAll(ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        orderService.getMyOrders(userId, null, "NORMAL", 1, 20);

        verify(orderRepository).findAll(ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class));
    }

    @Test
    void getMyOrders_withMixedCategoryStatuses_stillReturnsResultsForBoth() {
        when(orderRepository.findAll(ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        orderService.getMyOrders(userId, List.of("CANCELLED", "DELIVERED"), null, 1, 20);

        verify(orderRepository).findAll(ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class));
    }

    @Test
    void getMyOrders_withLowercaseType_isCaseInsensitive() {
        when(orderRepository.findAll(ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        orderService.getMyOrders(userId, null, "normal", 1, 20);

        verify(orderRepository).findAll(ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class));
    }

    @Test
    void getMyOrders_withInvalidType_throwsBadRequestExceptionInsteadOfCrashing() {
        assertThrows(BadRequestException.class, () -> orderService.getMyOrders(userId, null, "garbage", 1, 20));
        verifyNoInteractions(orderRepository);
    }

    @Test
    void getMyOrders_withInvalidStatus_throwsBadRequestExceptionInsteadOfCrashing() {
        assertThrows(BadRequestException.class, () -> orderService.getMyOrders(userId, List.of("garbage"), null, 1, 20));
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
