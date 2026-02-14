package com.bablu.upilite.service;

import com.bablu.upilite.util.AppConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
// Deployment strategy:
// Consumer starts only when APP_KAFKA_ENABLED=true.
// Set APP_KAFKA_ENABLED=false to disable Kafka listener in hosted/demo environments.
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationConsumer {

    // 👂 Ye method Kafka Topic par kaan laga kar baitha hai
    @KafkaListener(topics = AppConstants.Melody, groupId = "notification-group")
    public void receiveNotification(String message) {

        System.out.println("=================================================");
        System.out.println("📨 NEW MESSAGE RECEIVED FROM KAFKA TOPIC");
        System.out.println("📄 Content: " + message);

        // ⏳ Simulation: Maan lo Email bhejne mein 5 second lagte hain
        try {
            System.out.println("⚙️ Sending Email to User... (Processing)");
            Thread.sleep(5000); // 5 Second ki neend
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("✅ Email Sent Successfully!");
        System.out.println("=================================================");
    }
}
