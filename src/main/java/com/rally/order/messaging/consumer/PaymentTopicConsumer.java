package com.rally.order.messaging.consumer;

import com.rally.order.messaging.config.KafkaTopics;
import com.rally.order.messaging.event.inbound.payment.PaymentFailed;
import com.rally.order.messaging.event.inbound.payment.PaymentSucceeded;
import com.rally.order.messaging.support.EventTypes;
import com.rally.order.service.DealOrderService;
import com.rally.order.service.NormalOrderService;
import com.rally.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class PaymentTopicConsumer implements TopicConsumer {
    private final NormalOrderService normalOrderService;
    private final DealOrderService dealOrderService;
    private final OrderService orderService;

    @Override
    public String getTopic() {
        return KafkaTopics.PAYMENT;
    }

    @Override
    public void onMessage(ConsumerRecord<String, Object> record) {
        String eventType = extractType(record);
        if (eventType == null)
            return;

        switch (eventType) {
            case EventTypes.PAYMENT_CHARGED ->
                    normalOrderService.handlePaymentCharged((PaymentSucceeded) record.value());
            case EventTypes.PAYMENT_AUTHORIZED ->
                    dealOrderService.handlePaymentAuthorized((PaymentSucceeded) record.value());
            case EventTypes.PAYMENT_VOIDED -> dealOrderService.handlePaymentVoided((PaymentSucceeded) record.value());
            case EventTypes.PAYMENT_FAILED -> orderService.handlePaymentFailed((PaymentFailed) record.value());
            default -> System.out.println("Unhandled payment event type: " + eventType);
        }
    }

    private String extractType(ConsumerRecord<String, Object> record) {
        Header header = record.headers().lastHeader(KafkaTopics.HEADER_EVENT_TYPE);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
