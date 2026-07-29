package com.rally.order.service;

import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    public void handlePaymentFailed(PaymentFailed eventPayload) {
        // Implement the logic to handle payment failure
        System.out.println("Handling payment failed for order: " + eventPayload.orderId());
        // You can add more logic here, such as updating order status, notifying the user, etc.

    }
}
