package com.rally.order.controller;

import com.rally.order.dto.BriefOrderResponse;
import com.rally.order.dto.CheckOutOrderRequest;
import com.rally.order.dto.CheckOutOrderResponse;
import com.rally.order.dto.DetailedOrderResponse;
import com.rally.order.service.NormalOrderService;
import com.rally.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {
    private final NormalOrderService normalOrderService;
    private final OrderService orderService;

    @PostMapping("/orders/checkout")
    public ResponseEntity<CheckOutOrderResponse> orderCheckout(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody CheckOutOrderRequest orderRequest) {
        CheckOutOrderResponse response = this.normalOrderService.checkoutOrder(userId, orderRequest);
        return ResponseEntity.status(202).body(response);
    }

    @GetMapping("/orders/my")
    public ResponseEntity<List<BriefOrderResponse>> getMyOrders(@RequestHeader("X-User-Id") UUID userId){
        return ResponseEntity.status(200).body(this.orderService.getMyOrders(userId));
    }

    @GetMapping("/orders/my/{id}")
    public ResponseEntity<DetailedOrderResponse> getOrderDetails(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id){
        return ResponseEntity.status(200).body(this.orderService.getOrderDetails(userId, id));
    }

}
