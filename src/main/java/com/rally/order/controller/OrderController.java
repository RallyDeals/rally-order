package com.rally.order.controller;

import com.rally.order.dto.CheckOutOrderRequest;
import com.rally.order.dto.CheckOutOrderResponse;
import com.rally.order.service.NormalOrderService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {
    private final NormalOrderService orderService;

    @PostMapping("/orders/checkout")
    public ResponseEntity<CheckOutOrderResponse> orderCheckout(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody CheckOutOrderRequest orderRequest){
        CheckOutOrderResponse response = this.orderService.checkoutOrder(userId, orderRequest);
        return ResponseEntity.status(202).body(response);
    }

}
