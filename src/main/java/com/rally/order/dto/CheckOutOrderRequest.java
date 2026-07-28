package com.rally.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckOutOrderRequest {
    @NotNull(message = "Order items must be provided")
    @Size(min = 1, message = "Order items must be provided")
    private List<OrderItem> orderItems;
    @NotNull
    private String paymentMethodId;
}
