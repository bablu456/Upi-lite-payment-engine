package com.bablu.upilite.service;

import com.bablu.upilite.util.AppConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationProducer {

    // KafkaTemplate Spring ka magic tool hai message bhejne ke liye
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendNotification(String message) {
        // Log print karte hain taaki console mein dikhe
        System.out.println("DEBUG: Sending message to Kafka topic: " + message);

        // Asli kaam yahan ho raha hai
        kafkaTemplate.send(AppConstants.Melody, message);
    }
}