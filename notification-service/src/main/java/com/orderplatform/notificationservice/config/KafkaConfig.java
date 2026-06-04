package com.orderplatform.notificationservice.config;

import com.orderplatform.notificationservice.event.InventoryEvent;
import com.orderplatform.notificationservice.event.OrderEvent;
import com.orderplatform.notificationservice.event.PaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    @Bean
    public ConsumerFactory<String, OrderEvent> orderConsumerFactory() {
        JsonDeserializer<OrderEvent> d = new JsonDeserializer<>(OrderEvent.class, false);
        d.addTrustedPackages("com.orderplatform.*");
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(), new StringDeserializer(), d);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> orderListenerFactory() {
        var f = new ConcurrentKafkaListenerContainerFactory<String, OrderEvent>();
        f.setConsumerFactory(orderConsumerFactory());
        return f;
    }

    @Bean
    public ConsumerFactory<String, PaymentEvent> paymentConsumerFactory() {
        JsonDeserializer<PaymentEvent> d = new JsonDeserializer<>(PaymentEvent.class, false);
        d.addTrustedPackages("com.orderplatform.*");
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(), new StringDeserializer(), d);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> paymentListenerFactory() {
        var f = new ConcurrentKafkaListenerContainerFactory<String, PaymentEvent>();
        f.setConsumerFactory(paymentConsumerFactory());
        return f;
    }

    @Bean
    public ConsumerFactory<String, InventoryEvent> inventoryConsumerFactory() {
        JsonDeserializer<InventoryEvent> d = new JsonDeserializer<>(InventoryEvent.class, false);
        d.addTrustedPackages("com.orderplatform.*");
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(), new StringDeserializer(), d);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryEvent> inventoryListenerFactory() {
        var f = new ConcurrentKafkaListenerContainerFactory<String, InventoryEvent>();
        f.setConsumerFactory(inventoryConsumerFactory());
        return f;
    }
}
