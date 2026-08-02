package com.rally.order.model;

public enum OrderStatus {
    // NORMAL flow
    RESERVING,
    PENDING_CHARGE,

    // DEAL flow
    PENDING_AUTHORIZATION,
    AUTHORIZED,
    PENDING_CAPTURE,
    PENDING_VOID,

    // terminal states (both flows)
    CONFIRMED,
    CANCELLED
}