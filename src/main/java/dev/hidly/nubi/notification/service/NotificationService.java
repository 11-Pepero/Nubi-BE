package dev.hidly.nubi.notification.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.notification.data.dto.response.NotificationResponseDto;
import dev.hidly.nubi.notification.data.dto.response.UnreadCountResponseDto;
import dev.hidly.nubi.notification.data.entity.Notification;
import dev.hidly.nubi.notification.data.enums.NotificationTargetType;
import dev.hidly.nubi.notification.data.exception.NotificationNotFoundException;
import dev.hidly.nubi.notification.data.enums.NotificationType;
import dev.hidly.nubi.notification.data.repository.NotificationRepository;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationRepository notificationRepository;
    private final OrganizationRepository organizationRepository;
    private final NotificationEmitters notificationEmitters;

    /**
     * 다른 도메인에서 알림을 만들 때 부르는 메서드입니다. 저장이 커밋되면 구독 중인 화면에 실시간으로 보냅니다.
     * 예: notificationService.notify(hostOrgId, NotificationType.NEW_APPLICATION, "새 신청이 들어왔어요", "...", NotificationTargetType.APPLICATION, applicationId)
     */
    @Transactional
    public void notify(Long organizationId, NotificationType type, String title, String body,
                       NotificationTargetType targetType, Long targetId) {
        Notification notification = notificationRepository.save(Notification.builder()
                .organization(organizationRepository.getReferenceById(organizationId))
                .type(type)
                .title(title)
                .body(body)
                .targetType(targetType)
                .targetId(targetId)
                .build());
        NotificationResponseDto payload = NotificationResponseDto.from(notification);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationEmitters.send(organizationId, payload);
            }
        });
    }

    public SseEmitter subscribe() {
        return notificationEmitters.connect(SecurityUtil.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public GlobalResponse<PageResponse<NotificationResponseDto>> getMyNotifications(int page, int size) {
        return GlobalResponse.ok(PageResponse.from(
                notificationRepository.findAllByOrganizationId(SecurityUtil.getOrganizationId(),
                                PageRequest.of(page, size, LATEST_FIRST))
                        .map(NotificationResponseDto::from)));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<UnreadCountResponseDto> getUnreadCount() {
        return GlobalResponse.ok(new UnreadCountResponseDto(
                notificationRepository.countByOrganizationIdAndReadAtIsNull(SecurityUtil.getOrganizationId())));
    }

    @Transactional
    public GlobalResponse<NotificationResponseDto> read(Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndOrganizationId(notificationId, SecurityUtil.getOrganizationId())
                .orElseThrow(NotificationNotFoundException::new);
        notification.read();
        return GlobalResponse.ok(NotificationResponseDto.from(notification));
    }

    @Transactional
    public GlobalResponse<UnreadCountResponseDto> readAll() {
        notificationRepository.markAllRead(SecurityUtil.getOrganizationId(), LocalDateTime.now());
        return GlobalResponse.ok(new UnreadCountResponseDto(0));
    }
}
