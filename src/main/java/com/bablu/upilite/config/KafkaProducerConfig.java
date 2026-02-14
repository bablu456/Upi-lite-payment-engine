package com.bablu.upilite.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.config.TopicBuilder;
import com.bablu.upilite.util.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
// Deployment strategy:
// Keep Kafka beans active only when APP_KAFKA_ENABLED=true.
// In cloud/demo deploys, set APP_KAFKA_ENABLED=false to run the app without Kafka infra.
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaProducerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Keep transfer API responsive even when Kafka/topic is unavailable.
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 3000);
        configProps.put(ProducerConfig.RETRIES_CONFIG, 1);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory());
        template.setProducerListener(new ProducerListener<>() {
            @Override
            public void onError(org.apache.kafka.clients.producer.ProducerRecord<String, String> producerRecord,
                                org.apache.kafka.clients.producer.RecordMetadata recordMetadata,
                                Exception exception) {
                LOGGER.warn("Kafka publish failed for topic {}: {}",
                        producerRecord == null ? AppConstants.Melody : producerRecord.topic(),
                        exception == null ? "unknown error" : exception.getMessage());
            }
        });
        return template;
    }

    @Bean
    public NewTopic transactionNotificationsTopic() {
        return TopicBuilder.name(AppConstants.Melody)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
