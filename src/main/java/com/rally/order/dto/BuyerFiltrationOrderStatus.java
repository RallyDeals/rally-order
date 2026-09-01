package com.rally.order.dto;

public enum BuyerFiltrationOrderStatus {
    PENDING_PAYMENT, // PENDING_CHARGE/AUTHORIZATION/CAPTURE/VOID
    PENDING_DELIVERY, // SHIPPING + PROCESSING
    DELIVERED,
    CANCELLED,
}
