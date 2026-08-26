package com.rally.order.controller;

import com.rally.order.dto.*;
import com.rally.order.service.NormalOrderService;
import com.rally.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit){
        return ResponseEntity.status(200).body(this.orderService.getMyOrders(userId, status, type, page, limit));
    }

    @GetMapping("/my/statistics")
    public ResponseEntity<BuyerOrdersAnalytics> getMyOrdersAnalytics(
            @RequestHeader("X-User-Id") UUID userId){
        return ResponseEntity.status(200).body(this.orderService.getMyOrdersStatistics(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetailedOrderResponse> getOrderDetails(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id){
        return ResponseEntity.status(200).body(this.orderService.getOrderDetails(userId, id));
    }

    @GetMapping("/sellers/{sellerId}")
    public ResponseEntity<BriefSellerOrderPageResponse> getSellerOrders(
            @RequestHeader("X-User-Id") UUID callerId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID sellerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit){
        return ResponseEntity.status(200).body(this.orderService.getSellerOrders(callerId, role, sellerId, status, startDate, search, page, limit));
    }

    @GetMapping("/sellers/{sellerId}/{orderId}")
    public ResponseEntity<DetailedSellerOrderResponse> getSellerOrderDetails(
            @RequestHeader("X-User-Id") UUID callerId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID sellerId,
            @PathVariable UUID orderId){
        return ResponseEntity.status(200).body(this.orderService.getSellerOrderDetails(callerId, role, sellerId, orderId));
    }

    @GetMapping("/sellers/{sellerId}/analytics")
    public ResponseEntity<SellerOrdersAnalytics> getSellerOrdersAnalytics(
            @RequestHeader("X-User-Id") UUID callerId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String startDate,
            @PathVariable UUID sellerId
    ){
        return ResponseEntity.status(200).body(this.orderService.getSellerOrdersAnalytics(callerId, role, startDate, sellerId));
    }
}
