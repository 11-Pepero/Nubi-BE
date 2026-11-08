package dev.hidly.nubi.application.service;

import dev.hidly.nubi.application.data.dto.request.ApplicationDecisionRequestDto;
import dev.hidly.nubi.application.data.dto.response.EventApplicationDetailResponseDto;
import dev.hidly.nubi.application.data.dto.response.EventApplicationListResponseDto;
import dev.hidly.nubi.application.data.dto.response.EventApplicationSummaryResponseDto;
import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.entity.ApplicationMessage;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.exception.ApplicationNotFoundException;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.application.data.repository.EventApplicationStats;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.exception.ProgramAccessDeniedException;
import dev.hidly.nubi.program.data.exception.ProgramNotFoundException;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 주최 기관이 자기 행사에 들어온 신청을 보고 정하는 기능 (API-43, 44, 45)
@Slf4j
@Service
@RequiredArgsConstructor
public class EventApplicationService {
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("id"));

    private final ApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;

    @Transactional(readOnly = true)
    public GlobalResponse<EventApplicationListResponseDto> getApplications(Long eventId, List<ApplicationStatus> statuses,
                                                                           int page, int size) {
        Program event = getHostedEvent(eventId);
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<Application> applications = statuses == null || statuses.isEmpty()
                ? applicationRepository.findByProgramId(eventId, pageable)
                : applicationRepository.findByProgramIdAndStatusIn(eventId, statuses, pageable);

        Map<Long, Member> contacts = firstMembers(applications.map(a -> a.getApplicantOrganization().getId()).toSet());
        PageResponse<EventApplicationSummaryResponseDto> rows = PageResponse.from(applications.map(application ->
                EventApplicationSummaryResponseDto.of(application, contacts.get(application.getApplicantOrganization().getId()))));

        EventApplicationStats stats = statsOf(List.of(eventId)).getOrDefault(eventId, EventApplicationStats.empty(eventId));
        return GlobalResponse.ok(new EventApplicationListResponseDto(
                event.getCapacity(),
                stats.applicantOrgCount(),
                stats.participantCount(),
                stats.companionCount(),
                stats.newApplicationCount(),
                statusCounts(eventId),
                rows));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<EventApplicationDetailResponseDto> getApplication(Long eventId, Long applicationId) {
        getHostedEvent(eventId);
        return GlobalResponse.ok(toDetail(getApplicationOf(eventId, applicationId)));
    }

    // ponytail: 신청 기관 알림(APPLICATION_DECIDED)은 C 파트 알림 서비스가 생기면 여기서 보낸다.
    @Transactional
    public GlobalResponse<EventApplicationDetailResponseDto> decide(Long eventId, Long applicationId,
                                                                   ApplicationDecisionRequestDto request) {
        Program event = getHostedEvent(eventId);
        Application application = getApplicationOf(eventId, applicationId);
        ApplicationStatus before = application.getStatus();

        application.decideByHost(request.getStatus());
        if (request.getReason() != null && !request.getReason().isBlank()) {
            application.addMessage(ApplicationMessage.builder()
                    .senderOrganization(event.getHostOrganization())
                    .body(request.getReason().trim())
                    .build());
        }
        applicationRepository.flush();
        log.info("[decideApplication] 신청 상태 변경: eventId={}, applicationId={}, {} -> {}",
                eventId, applicationId, before, application.getStatus());

        return GlobalResponse.ok(toDetail(application));
    }

    // 내 행사 목록(API-39)에서 행사별 신청 숫자를 한 번에 가져올 때도 쓴다.
    @Transactional(readOnly = true)
    public Map<Long, EventApplicationStats> statsOf(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return applicationRepository.statsByProgramIds(eventIds, ApplicationStatus.RELEASED).stream()
                .collect(Collectors.toMap(EventApplicationStats::programId, stats -> stats));
    }

    private Program getHostedEvent(Long eventId) {
        Program event = programRepository.findById(eventId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(ProgramNotFoundException::new);
        if (!event.isHostedBy(SecurityUtil.getOrganizationId())) {
            throw new ProgramAccessDeniedException();
        }
        return event;
    }

    private Application getApplicationOf(Long eventId, Long applicationId) {
        return applicationRepository.findWithProgramById(applicationId)
                .filter(application -> application.belongsTo(eventId))
                .orElseThrow(ApplicationNotFoundException::new);
    }

    private EventApplicationDetailResponseDto toDetail(Application application) {
        Long applicantId = application.getApplicantOrganization().getId();
        return EventApplicationDetailResponseDto.of(application, firstMembers(Set.of(applicantId)).get(applicantId));
    }

    private Map<Long, Member> firstMembers(Set<Long> organizationIds) {
        if (organizationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Member> first = new LinkedHashMap<>();
        applicationRepository.findMembersOfOrganizations(organizationIds)
                .forEach(member -> first.putIfAbsent(member.getOrganization().getId(), member));
        return first;
    }

    private Map<ApplicationStatus, Long> statusCounts(Long eventId) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status, 0L);
        }
        applicationRepository.countByStatus(eventId)
                .forEach(row -> counts.put((ApplicationStatus) row[0], (Long) row[1]));
        return counts;
    }
}
