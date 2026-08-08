package com.rally.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckOutOrderRequest {
    @NotNull(message = "Order items must be provided")
    @Size(min = 1, message = "Order items must be provided")
    @Valid
    private List<OrderItem> orderItems;
    @NotNull(message = "Payment method id must be provided")
    private String paymentMethodId;
    @NotNull(message = "Address must be provided")
    private String address;
}
