package com.rally.order.dto;

import com.rally.order.model.CancelReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetailedSellerOrderResponse {
    private UUID orderId;
    private CompactedOrderStatus status;
    private CancelReason cancelReason;
    private String address;
    private OffsetDateTime createdAt;
    private List<BriefSellerOrderItemResponse> items;
}
