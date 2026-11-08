package dev.hidly.nubi.notification.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.notification.data.dto.response.NotificationResponseDto;
import dev.hidly.nubi.notification.data.dto.response.UnreadCountResponseDto;
import dev.hidly.nubi.notification.data.entity.Notification;
import dev.hidly.nubi.notification.data.enums.NotificationTargetType;
import dev.hidly.nubi.notification.data.enums.NotificationType;
import dev.hidly.nubi.notification.data.repository.NotificationRepository;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationRepository notificationRepository;
    private final OrganizationRepository organizationRepository;

    /**
     * 다른 도메인에서 알림을 만들 때 부르는 메서드입니다.
     * 예: notificationService.notify(hostOrgId, NotificationType.NEW_APPLICATION, "새 신청이 들어왔어요", "...", NotificationTargetType.APPLICATION, applicationId)
     */
    @Transactional
    public void notify(Long organizationId, NotificationType type, String title, String body,
                       NotificationTargetType targetType, Long targetId) {
        notificationRepository.save(Notification.builder()
                .organization(organizationRepository.getReferenceById(organizationId))
                .type(type)
                .title(title)
                .body(body)
                .targetType(targetType)
                .targetId(targetId)
                .build());
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
}
