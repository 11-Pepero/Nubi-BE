package dev.hidly.nubi.notification.data.dto.response;

import dev.hidly.nubi.notification.data.entity.Notification;
import dev.hidly.nubi.notification.data.enums.NotificationTargetType;
import dev.hidly.nubi.notification.data.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponseDto(Long notificationId, NotificationType type, String title, String body,
                                      NotificationTargetType targetType, Long targetId, boolean read,
                                      LocalDateTime createdAt) {
    public static NotificationResponseDto from(Notification notification) {
        return new NotificationResponseDto(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getBody(), notification.getTargetType(), notification.getTargetId(),
                notification.isRead(), notification.getCreatedAt());
    }
}
