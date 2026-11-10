package dev.hidly.nubi.application.service;

import dev.hidly.nubi.application.data.dto.response.MyApplicationCountResponseDto;
import dev.hidly.nubi.application.data.dto.response.MyApplicationDetailResponseDto;
import dev.hidly.nubi.application.data.dto.response.MyApplicationSummaryResponseDto;
import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.exception.ApplicationAccessDeniedException;
import dev.hidly.nubi.application.data.exception.ApplicationNotFoundException;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 신청한 기관이 자기 신청을 보는 기능 (API-29, 30, 31)
@Service
@RequiredArgsConstructor
public class MyApplicationService {
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    // 후기를 쓸 수 있는 신청 상태 (후기 쓰기 API-50과 같게)
    private static final Set<ApplicationStatus> REVIEWABLE = Set.of(ApplicationStatus.CONFIRMED, ApplicationStatus.ATTENDED);

    private final ApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public GlobalResponse<PageResponse<MyApplicationSummaryResponseDto>> getMyApplications(List<ApplicationStatus> statuses,
                                                                                          int page, int size) {
        Long organizationId = SecurityUtil.getOrganizationId();
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<Application> applications = statuses == null || statuses.isEmpty()
                ? applicationRepository.findByApplicantOrganizationId(organizationId, pageable)
                : applicationRepository.findByApplicantOrganizationIdAndStatusIn(organizationId, statuses, pageable);
        List<Long> applicationIds = applications.map(Application::getId).getContent();
        Map<Long, Long> reviewIds = applicationIds.isEmpty() ? Map.of()
                : applicationRepository.findReviewIdsByApplicationIds(applicationIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return GlobalResponse.ok(PageResponse.from(applications.map(application ->
                MyApplicationSummaryResponseDto.from(application, reviewIds.get(application.getId())))));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<MyApplicationCountResponseDto> getSummary() {
        Long organizationId = SecurityUtil.getOrganizationId();
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status, 0L);
        }
        applicationRepository.countByStatusOfApplicant(organizationId)
                .forEach(row -> counts.put((ApplicationStatus) row[0], (Long) row[1]));

        long preparing = counts.get(ApplicationStatus.PREPARING);
        long waiting = counts.get(ApplicationStatus.SUBMITTED) + counts.get(ApplicationStatus.INQUIRY)
                + counts.get(ApplicationStatus.WAITING);
        LocalDateTime now = LocalDateTime.now();
        long upcoming = applicationRepository.countByApplicantAndStatusStartingAfter(organizationId,
                ApplicationStatus.CONFIRMED, now);
        long attended = counts.get(ApplicationStatus.ATTENDED);

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime weekStart = monday.atStartOfDay();
        long upcomingThisWeek = applicationRepository.countByApplicantAndStatusStartingBetween(organizationId,
                ApplicationStatus.CONFIRMED, now.isAfter(weekStart) ? now : weekStart, weekStart.plusWeeks(1));

        return GlobalResponse.ok(new MyApplicationCountResponseDto(preparing + waiting + upcoming + attended,
                preparing, waiting, upcoming, upcomingThisWeek, attended,
                applicationRepository.countReviewPending(organizationId, REVIEWABLE, now), counts));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<MyApplicationDetailResponseDto> getMyApplication(Long applicationId) {
        Application application = applicationRepository.findWithProgramById(applicationId)
                .orElseThrow(ApplicationNotFoundException::new);
        if (!application.getApplicantOrganization().getId().equals(SecurityUtil.getOrganizationId())) {
            throw new ApplicationAccessDeniedException();
        }
        return GlobalResponse.ok(MyApplicationDetailResponseDto.from(application));
    }
}
