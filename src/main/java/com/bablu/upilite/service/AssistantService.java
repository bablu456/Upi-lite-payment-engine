package com.bablu.upilite.service;

import com.bablu.upilite.dto.AssistantChatRequestDto;
import com.bablu.upilite.dto.AssistantChatResponseDto;
import com.bablu.upilite.dto.AssistantMessageDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class AssistantService {

    private static final String SYSTEM_PROMPT = """
            You are UPI-Lite Assistant, a friendly and expert guide for the UPI-Lite Payment Engine demo app.
            
            This is a full-stack UPI Lite clone built with:
            - Backend:
            Spring Boot (Java 21), PostgreSQL, Kafka for events, SSE for realtime alerts
            - Frontend: React + Tailwind + Vite
            
            Key rules & features:
            - Wallet max Rs 2000, no PIN for transfers < Rs 500
            - OTP + JWT auth, idempotency keys for safe retries
            - QR codes, contacts, transaction history, KYC mock
            - Realtime notifications via Kafka + SSE
            
            Your job:
            - Explain features clearly
            - Guide users step-by-step (e.g., "To send money: Go to Dashboard -> Enter amount/receiver -> Submit")
            - Answer questions about the code, architecture, or how things work
            - Be concise, helpful, and fun
            
            Always stay in character as the app's built-in assistant.
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.ai.enabled:true}")
    private boolean enabled;

    @Value("${app.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${app.ai.openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${app.ai.openai.chat-url:https://api.openai.com/v1/chat/completions}")
    private String chatUrl;

    @Value("${app.ai.max-history:8}")
    private int maxHistory;

    @Value("${app.ai.temperature:0.4}")
    private double temperature;

    @Value("${app.ai.max-tokens:350}")
    private int maxTokens;

    public AssistantChatResponseDto chat(AssistantChatRequestDto request) {
        String userMessage = normalizeMessage(request == null ? null : request.getMessage());

        if (!enabled || !StringUtils.hasText(openAiApiKey)) {
            return fallbackResponse(userMessage);
        }

        try {
            String assistantReply = callOpenAi(request, userMessage);
            if (!StringUtils.hasText(assistantReply)) {
                return fallbackResponse(userMessage);
            }

            return AssistantChatResponseDto.builder()
                    .reply(assistantReply.trim())
                    .provider("openai")
                    .model(openAiModel)
                    .fallback(false)
                    .build();
        } catch (Exception exception) {
            log.warn("Assistant API fallback triggered: {}", exception.getMessage());
            return fallbackResponse(userMessage);
        }
    }

    private String callOpenAi(AssistantChatRequestDto request, String userMessage)
            throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", openAiModel);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
        payload.put("messages", buildMessages(request, userMessage));

        String requestBody = objectMapper.writeValueAsString(payload);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiApiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Assistant API returned status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choicesNode = root.path("choices");
        if (!choicesNode.isArray() || choicesNode.isEmpty()) {
            throw new IllegalStateException("Assistant response has no choices.");
        }

        JsonNode contentNode = choicesNode.get(0).path("message").path("content");
        return contentNode.isTextual() ? contentNode.asText() : "";
    }

    private List<Map<String, String>> buildMessages(AssistantChatRequestDto request, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        List<AssistantMessageDto> history = request == null ? List.of() : request.getHistory();
        if (history != null && !history.isEmpty()) {
            int startIndex = Math.max(0, history.size() - Math.max(1, maxHistory));
            for (int index = startIndex; index < history.size(); index++) {
                AssistantMessageDto message = history.get(index);
                if (message == null) {
                    continue;
                }

                String role = message.getRole() == null ? "" : message.getRole().trim().toLowerCase(Locale.ROOT);
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }

                String content = message.getContent() == null ? "" : message.getContent().trim();
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                messages.add(Map.of("role", role, "content", truncate(content, 1200)));
            }
        }

        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    private AssistantChatResponseDto fallbackResponse(String userMessage) {
        return AssistantChatResponseDto.builder()
                .reply(generateLocalReply(userMessage))
                .provider("local-fallback")
                .model("rule-based")
                .fallback(true)
                .build();
    }

    private String normalizeMessage(String message) {
        String normalized = message == null ? "" : message.trim();
        if (!StringUtils.hasText(normalized)) {
            return "Help me understand the UPI-Lite app features.";
        }
        return truncate(normalized, 1200);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String generateLocalReply(String userMessage) {
        String text = userMessage.toLowerCase(Locale.ROOT);

        if (text.contains("send") || text.contains("transfer") || text.contains("pay")) {
            return """
                    UPI-Lite Assistant here. To send money quickly:
                    1. Go to Dashboard -> Send Money (or Scan & Pay).
                    2. Enter receiver UPI/mobile and amount.
                    3. If amount is Rs 500 or more, enter UPI PIN.
                    4. Submit and check realtime status in dashboard/history.
                    Tip: Wallet cap is Rs 2000, so keep balance within limit.
                    """;
        }

        if (text.contains("otp") || text.contains("login") || text.contains("password")) {
            return """
                    Sure. Auth flow in this app:
                    1. Request OTP from login or forgot-password screen.
                    2. Verify OTP to complete login/reset.
                    3. JWT is issued and used for protected APIs.
                    If OTP isn’t received, check SMTP settings and OTP_EMAIL_ENABLED=true.
                    """;
        }

        if (text.contains("scan") || text.contains("qr")) {
            return """
                    QR flow is ready:
                    1. Open Scan & Pay.
                    2. Scan from camera or image.
                    3. App parses UPI payload and prefills Send Money.
                    4. Confirm and pay.
                    You can also share your own receive QR from My QR page.
                    """;
        }

        if (text.contains("kyc")) {
            return """
                    KYC mock flow:
                    1. Open KYC page.
                    2. Upload document.
                    3. Track status (Pending/Approved/Rejected).
                    This helps demonstrate trust flow and risk signals.
                    """;
        }

        if (text.contains("tech") || text.contains("architecture") || text.contains("stack")) {
            return """
                    Architecture snapshot:
                    - Backend: Spring Boot + PostgreSQL + Kafka + SSE
                    - Frontend: React + Tailwind + Vite
                    - Security: OTP + JWT + idempotency keys
                    - Payments: UPI Lite policy rules + Scam Shield checks
                    Ask me any module and I will break it down step-by-step.
                    """;
        }

        return """
                Hey, I am your UPI-Lite Assistant.
                I can help with:
                - sending money, QR scan flow, contacts
                - OTP/JWT auth setup
                - KYC and transaction history
                - backend + frontend architecture
                Tell me what you want to do and I will guide you in steps.
                """;
    }
}
