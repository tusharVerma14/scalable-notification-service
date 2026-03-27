package com.techgiant.notification.service;

import com.techgiant.notification.config.RabbitMQConfig;
import com.techgiant.notification.dto.NotificationDTO;
import com.techgiant.notification.model.Notification;
import com.techgiant.notification.repository.NotificationRepository;
import com.techgiant.notification.service.strategy.DeliveryStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    // Spring injects all beans implementing DeliveryStrategy automatically
    private final List<DeliveryStrategy> deliveryStrategies;
    private final NotificationRepository notificationRepository;

    private Map<String, DeliveryStrategy> strategyMap;

    @jakarta.annotation.PostConstruct
    public void init() {
        strategyMap = deliveryStrategies.stream()
                .collect(Collectors.toMap(DeliveryStrategy::getChannelType, strategy -> strategy));
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void processNotification(NotificationDTO dto) {
        log.info("Processing notification from queue for user: {}", dto.getTargetUserId());

        Notification notification = Notification.builder()
                .targetUserId(dto.getTargetUserId())
                .title(dto.getTitle())
                .body(dto.getBody())
                .build();

        List<String> requestedChannels = dto.getChannels();
        if (requestedChannels == null || requestedChannels.isEmpty()) {
            requestedChannels = List.of("WEBSOCKET");
        }

        for (String channel : requestedChannels) {
            DeliveryStrategy strategy = strategyMap.get(channel.toUpperCase());
            if (strategy != null) {
                log.info("Delivering via channel: {}", channel);
                strategy.deliver(notification);
            } else {
                log.warn("Unknown delivery channel: {}. Routing to DLQ.", channel);
                // throwing here causes RabbitMQ to retry and eventually DLQ the message
                throw new IllegalArgumentException("Unknown channel: " + channel);
            }
        }
    }
}
