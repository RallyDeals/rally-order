package com.rally.order.service;

import com.rally.order.messaging.event.inbound.deal.DealFailed;
import com.rally.order.messaging.event.inbound.deal.DealSucceeded;
import com.rally.order.messaging.event.inbound.participation.ParticipantJoined;
import com.rally.order.messaging.event.inbound.participation.ParticipantLeft;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.messaging.event.inbound.payment.PaymentSucceeded;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DealOrderService  {
    private final DealOrderTransitionService dealOrderTransitionService;
    private final OrderRepository orderRepository;

    public void handleParticipationJoin(ParticipantJoined eventPayload){
        dealOrderTransitionService.handleParticipantJoined(eventPayload);
    }

    public void handleParticipationLeft(ParticipantLeft eventPayload){
        dealOrderTransitionService.handleParticipantLeave(eventPayload);
    }

    public void handlePaymentAuthorized(PaymentSucceeded eventPayload){
        dealOrderTransitionService.handlePaymentAuthorized(eventPayload);
    }

    public void handlePaymentVoided(PaymentSucceeded eventPayload){
        dealOrderTransitionService.handlePaymentVoided(eventPayload);
    }

    public void handlePaymentFailed(PaymentFailed eventPayload){
        dealOrderTransitionService.handleFailedAuthorization(eventPayload);
    }

    }
}
