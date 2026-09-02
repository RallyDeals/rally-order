package com.rally.order.messaging.consumer;

import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.messaging.event.inbound.payment.PaymentSucceeded;
import com.rally.order.messaging.support.EventTypes;
import com.rally.order.service.DealOrderService;
import com.rally.order.service.NormalOrderService;
import com.rally.order.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentTopicConsumerTest {

    @Mock
    private NormalOrderService normalOrderService;
    @Mock
    private DealOrderService dealOrderService;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentTopicConsumer consumer;

    @Test
    void getTopic_returnsPaymentTopic() {
        assertEquals(KafkaTopics.PAYMENT, consumer.getTopic());
    }

    @Test
    void onMessage_paymentCharged_dispatchesToNormalOrderService() {
        PaymentSucceeded payload = new PaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
        ConsumerRecord<String, Object> record = record(EventTypes.PAYMENT_CHARGED, payload);

        consumer.onMessage(record);

        verify(normalOrderService).handlePaymentCharged(payload);
        verifyNoInteractions(dealOrderService, orderService);
    }

    @Test
    void onMessage_paymentAuthorized_dispatchesToDealOrderService() {
        PaymentSucceeded payload = new PaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
        ConsumerRecord<String, Object> record = record(EventTypes.PAYMENT_AUTHORIZED, payload);

        consumer.onMessage(record);

        verify(dealOrderService).handlePaymentAuthorized(payload);
        verifyNoInteractions(normalOrderService, orderService);
    }

    @Test
    void onMessage_paymentCaptured_dispatchesToDealOrderService() {
        PaymentSucceeded payload = new PaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
        ConsumerRecord<String, Object> record = record(EventTypes.PAYMENT_CAPTURED, payload);

        consumer.onMessage(record);

        verify(dealOrderService).handlePaymentCaptured(payload);
        verifyNoInteractions(normalOrderService, orderService);
    }

    @Test
    void onMessage_paymentVoided_dispatchesToDealOrderService() {
        PaymentSucceeded payload = new PaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
        ConsumerRecord<String, Object> record = record(EventTypes.PAYMENT_VOIDED, payload);

        consumer.onMessage(record);

        verify(dealOrderService).handlePaymentVoided(payload);
        verifyNoInteractions(normalOrderService, orderService);
    }

    @Test
    void onMessage_paymentFailed_dispatchesToOrderService() {
        PaymentFailed payload = new PaymentFailed(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "card_declined", "402");
        ConsumerRecord<String, Object> record = record(EventTypes.PAYMENT_FAILED, payload);

        consumer.onMessage(record);

        verify(orderService).handlePaymentFailed(payload);
        verifyNoInteractions(normalOrderService, dealOrderService);
    }

    @Test
    void onMessage_missingTypeHeader_doesNothing() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(KafkaTopics.PAYMENT, 0, 0L, "key",
                new PaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN));

        consumer.onMessage(record);

        verifyNoInteractions(normalOrderService, dealOrderService, orderService);
    }

    @Test
    void onMessage_unknownEventType_doesNothing() {
        ConsumerRecord<String, Object> record = record("Payment.SomethingElse",
                new PaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN));

        consumer.onMessage(record);

        verifyNoInteractions(normalOrderService, dealOrderService, orderService);
    }

    private ConsumerRecord<String, Object> record(String eventType, Object payload) {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(KafkaTopics.PAYMENT, 0, 0L, "key", payload);
        record.headers().add(new RecordHeader(KafkaTopics.HEADER_EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8)));
        return record;
    }
}
