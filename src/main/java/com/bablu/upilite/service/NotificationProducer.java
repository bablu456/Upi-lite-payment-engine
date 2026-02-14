package com.bablu.upilite.service;

import com.bablu.upilite.util.AppConstants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class NotificationProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationProducer.class);

    // ObjectProvider keeps bean optional when Kafka is disabled for deployment.
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    @Value("${app.kafka.enabled:true}")
    private boolean kafkaEnabled;

    @Async("notificationTaskExecutor")
    public void sendNotificationAsync(String message) {
        // NOTE:
        // For production-like local runs keep APP_KAFKA_ENABLED=true.
        // For easy cloud/demo deploy without Kafka infra set APP_KAFKA_ENABLED=false.
        if (!kafkaEnabled) {
            LOGGER.info("Kafka disabled. Notification skipped: {}", message);
            return;
        }

        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            LOGGER.warn("KafkaTemplate unavailable. Notification skipped: {}", message);
            return;
        }

        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(AppConstants.Melody, message);
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    LOGGER.warn("Notification publish failed for topic {}: {}", AppConstants.Melody, exception.getMessage());
                    return;
                }

                if (result != null && result.getRecordMetadata() != null) {
                    LOGGER.debug("Notification published to topic {} partition {} offset {}",
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception exception) {
            LOGGER.warn("Notification dispatch skipped due to producer error: {}", exception.getMessage());
        }
    }
}
