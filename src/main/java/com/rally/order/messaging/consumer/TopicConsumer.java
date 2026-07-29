package com.rally.order.messaging.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public interface TopicConsumer {
    String getTopic();
    void onMessage(ConsumerRecord<String, Object> record);
}
