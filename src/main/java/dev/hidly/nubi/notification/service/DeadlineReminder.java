package dev.hidly.nubi.notification.service;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.notification.data.enums.NotificationTargetType;
import dev.hidly.nubi.notification.data.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// 신청서를 쓰다 만 기관에 마감 3일 전, 하루 전 아침 9시에 알린다
@Component
@RequiredArgsConstructor
public class DeadlineReminder {
    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional
    public void remind() {
        remind(LocalDate.now());
    }

    @Transactional
    public void remind(LocalDate today) {
        remindDaysBefore(today, 3, "신청 마감 3일 전이에요");
        remindDaysBefore(today, 1, "신청 마감이 내일이에요");
    }

    private void remindDaysBefore(LocalDate today, int days, String title) {
        LocalDate deadlineDay = today.plusDays(days);
        for (Application application : applicationRepository.findPreparingByDeadlineBetween(
                deadlineDay.atStartOfDay(), deadlineDay.plusDays(1).atStartOfDay())) {
            notificationService.notify(application.getApplicantOrganization().getId(), NotificationType.DEADLINE, title,
                    "'" + application.getProgram().getTitle() + "' 신청서를 아직 제출하지 않았어요.",
                    NotificationTargetType.APPLICATION, application.getId());
        }
    }
}
