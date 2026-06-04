package com.orderplatform.orderservice.config;

import com.orderplatform.orderservice.event.InventoryEvent;
import com.orderplatform.orderservice.event.PaymentEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ── Topics ───────────────────────────────────────────────────────────────
    @Bean public NewTopic orderEventsTopic()    { return TopicBuilder.name("order-events").partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentEventsTopic()  { return TopicBuilder.name("payment-events").partitions(3).replicas(1).build(); }
    @Bean public NewTopic inventoryEventsTopic(){ return TopicBuilder.name("inventory-events").partitions(3).replicas(1).build(); }
    @Bean public NewTopic orderDltTopic()       { return TopicBuilder.name("order-events-dlt").partitions(1).replicas(1).build(); }
    @Bean public NewTopic paymentDltTopic()     { return TopicBuilder.name("payment-events-dlt").partitions(1).replicas(1).build(); }
    @Bean public NewTopic inventoryDltTopic()   { return TopicBuilder.name("inventory-events-dlt").partitions(1).replicas(1).build(); }

    // ── Producer ─────────────────────────────────────────────────────────────
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer (PaymentEvent) ──────────────────────────────────────────────
    @Bean
    public ConsumerFactory<String, PaymentEvent> paymentConsumerFactory() {
        JsonDeserializer<PaymentEvent> deser = new JsonDeserializer<>(PaymentEvent.class, false);
        deser.addTrustedPackages("com.orderplatform.*");
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deser);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> paymentKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentConsumerFactory());
        return factory;
    }

    // ── Consumer (InventoryEvent) ────────────────────────────────────────────
    @Bean
    public ConsumerFactory<String, InventoryEvent> inventoryConsumerFactory() {
        JsonDeserializer<InventoryEvent> deser = new JsonDeserializer<>(InventoryEvent.class, false);
        deser.addTrustedPackages("com.orderplatform.*");
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deser);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryEvent> inventoryKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InventoryEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryConsumerFactory());
        return factory;
    }
}
