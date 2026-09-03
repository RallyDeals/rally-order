package com.rally.order.service;

import com.rally.order.client.CatalogServiceClient;
import com.rally.order.client.dto.CatalogLookupRequest;
import com.rally.order.client.dto.CatalogLookupResponse;
import com.rally.order.client.dto.CatalogProduct;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DealOrderService  {
    private static final Logger log = LoggerFactory.getLogger(DealOrderService.class);

    private final DealOrderTransitionService dealOrderTransitionService;
    private final OrderRepository orderRepository;
    private final CatalogServiceClient catalogServiceClient;

    public void handleParticipationJoin(ParticipantJoined eventPayload){
        CatalogProduct catalogProduct = lookupCatalogProduct(eventPayload.productId());
        dealOrderTransitionService.handleParticipantJoined(eventPayload, catalogProduct);
    }

    private CatalogProduct lookupCatalogProduct(UUID productId) {
        try {
            CatalogLookupResponse response = catalogServiceClient.lookup(
                    CatalogLookupRequest.builder().productIds(List.of(productId)).build());
            return response.getFound().get(productId);
        } catch (RuntimeException e) {
            log.warn("Catalog lookup failed while enriching deal order product {}, proceeding without product snapshot", productId, e);
            return null;
        }
    }

    public void handleParticipationLeft(ParticipantLeft eventPayload){
        dealOrderTransitionService.handleParticipantLeave(eventPayload);
    }

    public void handleDealSucceeded(DealSucceeded eventPayload){
        List<Order> orders = orderRepository.findOrdersByDealIdAndStatus(eventPayload.dealId(), OrderStatus.AUTHORIZED);
        log.debug("Deal {} succeeded, moving {} authorized order(s) to capture", eventPayload.dealId(), orders.size());
        for(Order order : orders)
            dealOrderTransitionService.handleDealSucceeded(order);
    }

    public void handleDealFailed(DealFailed eventPayload){
        List<Order> orders = orderRepository.findOrdersByDealIdAndStatus(eventPayload.dealId(), OrderStatus.AUTHORIZED);
        log.debug("Deal {} failed, voiding {} authorized order(s)", eventPayload.dealId(), orders.size());
        for(Order order : orders)
            dealOrderTransitionService.handleDealFailed(order);
    }

    public void handlePaymentAuthorized(PaymentSucceeded eventPayload){
        dealOrderTransitionService.handlePaymentAuthorized(eventPayload);
    }

    public void handlePaymentCaptured(PaymentSucceeded eventPayload){
        dealOrderTransitionService.handlePaymentCaptured(eventPayload);
    }

    public void handlePaymentVoided(PaymentSucceeded eventPayload){
        dealOrderTransitionService.handlePaymentVoided(eventPayload);
    }

    public void handlePaymentFailed(PaymentFailed eventPayload){
        dealOrderTransitionService.handleFailedAuthorization(eventPayload);
    }

    public void cancelStuckPendingAuthorizationOrder(Order order){
        dealOrderTransitionService.cancelOrderForPaymentTimeout(order);
    }
}
