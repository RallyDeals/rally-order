package com.rally.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckOutOrderRequest {
    @NotNull(message = "User id must be provided")
    private UUID userId;
    @NotNull(message = "Order items must be provided")
    @Size(min = 1, message = "Order items must be provided")
    private List<OrderItem> orderItems;
}
