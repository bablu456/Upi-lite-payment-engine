package com.bablu.upilite.service;

import com.bablu.upilite.util.AppConstants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class NotificationProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Async("notificationTaskExecutor")
    public void sendNotificationAsync(String message) {
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
