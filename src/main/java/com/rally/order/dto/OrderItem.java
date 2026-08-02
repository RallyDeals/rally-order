package com.rally.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {
    @NotNull(message = "Product id must be provided")
    private UUID productId;
    @Min(value = 1, message = "Quantity must be greater than 0")
    private int quantity;
}
