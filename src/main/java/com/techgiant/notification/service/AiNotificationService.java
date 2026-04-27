package com.techgiant.notification.service;

import com.techgiant.notification.dto.AiNotificationRequestDTO;
import com.techgiant.notification.dto.NotificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AiNotificationService {

    private final ChatClient chatClient;
    private final NotificationProducer notificationProducer;

    public AiNotificationService(ChatClient.Builder chatClientBuilder, NotificationProducer notificationProducer) {
        this.chatClient = chatClientBuilder.build();
        this.notificationProducer = notificationProducer;
    }

    public void generateAndQueueNotification(AiNotificationRequestDTO request) {
        log.info("Generating AI notification for user: {}", request.getTargetUserId());

        String prompt = String.format(
                "Generate a professional, friendly, and engaging notification message based on the following event and context. " +
                "Respond ONLY with a JSON object containing two fields: 'title' and 'body'. " +
                "Do not use markdown formatting like ```json in the response, just the raw JSON object.\n" +
                "Event: %s\n" +
                "Context: %s", 
                request.getEvent(), request.getContext()
        );

        try {
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // Very basic manual parsing to extract title and body since we requested raw JSON
            // In a production system, we'd use Spring AI's structured output converters
            
            // Clean up any potential markdown code blocks returned by the model
            if (aiResponse.startsWith("```json")) {
                aiResponse = aiResponse.substring(7);
            }
            if (aiResponse.startsWith("```")) {
                aiResponse = aiResponse.substring(3);
            }
            if (aiResponse.endsWith("```")) {
                aiResponse = aiResponse.substring(0, aiResponse.length() - 3);
            }
            aiResponse = aiResponse.trim();

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(aiResponse);
            
            String title = jsonNode.has("title") ? jsonNode.get("title").asText() : "New Update";
            String body = jsonNode.has("body") ? jsonNode.get("body").asText() : "You have a new update.";

            NotificationDTO notificationDTO = NotificationDTO.builder()
                    .targetUserId(request.getTargetUserId())
                    .title(title)
                    .body(body)
                    .channels(request.getChannels())
                    .build();

            notificationProducer.queueNotification(notificationDTO);
            log.info("Successfully generated and queued AI notification for user: {}", request.getTargetUserId());

        } catch (Exception e) {
            log.error("Failed to generate AI notification: {}", e.getMessage());
            // Fallback: Queue a generic notification if AI fails
            NotificationDTO fallbackDto = NotificationDTO.builder()
                    .targetUserId(request.getTargetUserId())
                    .title("New Notification")
                    .body("Event: " + request.getEvent() + " - " + request.getContext())
                    .channels(request.getChannels())
                    .build();
            notificationProducer.queueNotification(fallbackDto);
        }
    }
}
