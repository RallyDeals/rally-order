package com.rally.order.service;

import com.rally.order.client.CatalogServiceClient;
import com.rally.order.client.dto.CatalogLookupRequest;
import com.rally.order.client.dto.CatalogLookupResponse;
import com.rally.order.client.dto.CatalogProduct;
import com.rally.order.messaging.event.inbound.deal.DealFailed;
import com.rally.order.messaging.event.inbound.deal.DealSucceeded;
import com.rally.order.messaging.event.inbound.participation.ParticipantJoined;
import com.rally.order.model.Order;
import com.rally.order.model.OrderStatus;
import com.rally.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealOrderServiceTest {

    @Mock
    private DealOrderTransitionService dealOrderTransitionService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CatalogServiceClient catalogServiceClient;

    private DealOrderService dealOrderService;

    private UUID dealId;
    private UUID participantId;
    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        dealOrderService = new DealOrderService(dealOrderTransitionService, orderRepository, catalogServiceClient);
        dealId = UUID.randomUUID();
        participantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
    }

    // ---- handleParticipationJoin ----

    @Test
    void handleParticipationJoin_whenCatalogLookupSucceeds_passesResolvedProductToTransitionService() {
        ParticipantJoined event = new ParticipantJoined(participantId, dealId, userId, productId, BigDecimal.valueOf(50), "pm_123", "123 Main St", Instant.parse("2024-01-01T00:00:00Z"));
        CatalogProduct catalogProduct = CatalogProduct.builder().id(productId).name("Widget").imageUrl("http://img").basePrice(BigDecimal.valueOf(50)).build();
        when(catalogServiceClient.lookup(any(CatalogLookupRequest.class)))
                .thenReturn(CatalogLookupResponse.builder().found(Map.of(productId, catalogProduct)).notFound(List.of()).build());

        dealOrderService.handleParticipationJoin(event);

        verify(dealOrderTransitionService).handleParticipantJoined(event, catalogProduct);
        ArgumentCaptor<CatalogLookupRequest> requestCaptor = ArgumentCaptor.forClass(CatalogLookupRequest.class);
        verify(catalogServiceClient).lookup(requestCaptor.capture());
        assertEquals(List.of(productId), requestCaptor.getValue().getProductIds());
    }

    @Test
    void handleParticipationJoin_whenCatalogLookupThrows_proceedsWithNullProductSnapshot() {
        ParticipantJoined event = new ParticipantJoined(participantId, dealId, userId, productId, BigDecimal.valueOf(50), "pm_123", "123 Main St", Instant.parse("2024-01-01T00:00:00Z"));
        when(catalogServiceClient.lookup(any(CatalogLookupRequest.class))).thenThrow(new RuntimeException("Catalog service is unavailable"));

        dealOrderService.handleParticipationJoin(event);

        verify(dealOrderTransitionService).handleParticipantJoined(event, null);
    }

    // ---- handleDealSucceeded / handleDealFailed ----

    @Test
    void handleDealSucceeded_processesEveryAuthorizedOrderForTheDeal() {
        DealSucceeded event = new DealSucceeded(Instant.now(), dealId, Integer.valueOf(10), Integer.valueOf(5), productId, Integer.valueOf(5));
        Order order1 = Order.builder().id(UUID.randomUUID()).build();
        Order order2 = Order.builder().id(UUID.randomUUID()).build();
        when(orderRepository.findOrdersByDealIdAndStatus(dealId, OrderStatus.AUTHORIZED)).thenReturn(List.of(order1, order2));

        dealOrderService.handleDealSucceeded(event);

        verify(dealOrderTransitionService).handleDealSucceeded(order1);
        verify(dealOrderTransitionService).handleDealSucceeded(order2);
    }

    @Test
    void handleDealFailed_processesEveryAuthorizedOrderForTheDeal() {
        DealFailed event = new DealFailed(Instant.now(), dealId, Integer.valueOf(10), Integer.valueOf(5), productId, Integer.valueOf(5));
        Order order1 = Order.builder().id(UUID.randomUUID()).build();
        when(orderRepository.findOrdersByDealIdAndStatus(dealId, OrderStatus.AUTHORIZED)).thenReturn(List.of(order1));

        dealOrderService.handleDealFailed(event);

        verify(dealOrderTransitionService, times(1)).handleDealFailed(order1);
    }
}
