package dev.hidly.nubi.notification.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.notification.data.enums.NotificationTargetType;
import dev.hidly.nubi.notification.data.enums.NotificationType;
import dev.hidly.nubi.organization.data.entity.Organization;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notification", indexes = @Index(columnList = "organization_id, read_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NotificationTargetType targetType;

    private Long targetId;

    private LocalDateTime readAt;

    @Builder
    private Notification(Organization organization, NotificationType type, String title, String body,
                         NotificationTargetType targetType, Long targetId) {
        this.organization = organization;
        this.type = type;
        this.title = title;
        this.body = body;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void read() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
