package dev.hidly.nubi.notification.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.notification.data.dto.response.NotificationResponseDto;
import dev.hidly.nubi.notification.data.dto.response.UnreadCountResponseDto;
import dev.hidly.nubi.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "알림", description = "알림 목록, 실시간 알림")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "알림 목록", description = "로그인 기관이 받은 알림을 최신순으로 조회합니다.")
    public GlobalResponse<PageResponse<NotificationResponseDto>> getMyNotifications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notificationService.getMyNotifications(page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "안 읽은 알림 수", description = "알림 벨에 표시할 안 읽은 알림 개수입니다.")
    public GlobalResponse<UnreadCountResponseDto> getUnreadCount() {
        return notificationService.getUnreadCount();
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음", description = "알림 하나를 읽음으로 표시합니다. 이미 읽은 알림이면 그대로 둡니다.")
    public GlobalResponse<NotificationResponseDto> read(@PathVariable Long notificationId) {
        return notificationService.read(notificationId);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "모두 읽음", description = "로그인 기관의 안 읽은 알림을 모두 읽음으로 표시합니다. 응답의 unreadCount는 0입니다.")
    public GlobalResponse<UnreadCountResponseDto> readAll() {
        return notificationService.readAll();
    }
}
