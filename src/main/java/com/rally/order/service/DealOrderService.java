package com.rally.order.service;

import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import org.springframework.stereotype.Service;

@Service
public class DealOrderService {
    public void handlePaymentFailed(PaymentFailed eventPayload){

    }
}
