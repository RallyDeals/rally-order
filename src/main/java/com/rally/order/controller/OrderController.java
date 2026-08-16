package com.rally.order.controller;

import com.rally.order.dto.BriefOrderPageResponse;
import com.rally.order.dto.BriefSellerOrderPageResponse;
import com.rally.order.dto.CheckOutOrderRequest;
import com.rally.order.dto.CheckOutOrderResponse;
import com.rally.order.dto.DetailedOrderResponse;
import com.rally.order.dto.DetailedSellerOrderResponse;
import com.rally.order.service.NormalOrderService;
import com.rally.order.service.OrderService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final NormalOrderService normalOrderService;
    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckOutOrderResponse> orderCheckout(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody CheckOutOrderRequest orderRequest) {
        CheckOutOrderResponse response = this.normalOrderService.checkoutOrder(userId, orderRequest);
        return ResponseEntity.status(202).body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<BriefOrderPageResponse> getMyOrders(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit){
        return ResponseEntity.status(200).body(this.orderService.getMyOrders(userId, status, orderType, page, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetailedOrderResponse> getOrderDetails(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id){
        return ResponseEntity.status(200).body(this.orderService.getOrderDetails(userId, id));
    }

    @GetMapping("/sellers/{sellerId}")
    @RolesAllowed("ROLE_SELLER")
    public ResponseEntity<BriefSellerOrderPageResponse> getSellerOrders(
            @PathVariable UUID sellerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit){
        return ResponseEntity.status(200).body(this.orderService.getSellerOrders(sellerId, status, page, limit));
    }

    @GetMapping("/sellers/{sellerId}/{orderId}")
    @RolesAllowed("ROLE_SELLER")
    public ResponseEntity<DetailedSellerOrderResponse> getSellerOrderDetails(
            @PathVariable UUID sellerId,
            @PathVariable UUID orderId){
        return ResponseEntity.status(200).body(this.orderService.getSellerOrderDetails(sellerId, orderId));
    }
}
