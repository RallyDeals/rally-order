package com.rally.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuyerOrdersAnalytics {
    private int deliveredOrders;
    private int cancelledOrders;
    private int pendingDelivery;
    private int pendingPayment;
}
