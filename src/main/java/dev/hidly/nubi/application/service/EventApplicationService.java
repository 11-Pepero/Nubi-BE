package dev.hidly.nubi.application.service;

import dev.hidly.nubi.application.data.dto.request.ApplicationDecisionRequestDto;
import dev.hidly.nubi.application.data.dto.response.EventApplicationDetailResponseDto;
import dev.hidly.nubi.application.data.dto.response.EventApplicationListResponseDto;
import dev.hidly.nubi.application.data.dto.response.EventApplicationSummaryResponseDto;
import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.dto.request.MessageRequestDto;
import dev.hidly.nubi.application.data.dto.request.SupportPrepRequestDto;
import dev.hidly.nubi.application.data.dto.response.EventNeedsResponseDto;
import dev.hidly.nubi.application.data.exception.ApplicationSupportNotFoundException;
import dev.hidly.nubi.application.data.exception.InvalidApplicationStatusException;
import dev.hidly.nubi.application.data.entity.ApplicationMessage;
import dev.hidly.nubi.application.data.entity.ApplicationSupport;
import dev.hidly.nubi.application.data.enums.PrepStatus;
import java.util.ArrayList;
import java.util.Comparator;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.exception.ApplicationNotFoundException;
import dev.hidly.nubi.application.data.exception.CapacityExceededException;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.application.data.repository.EventApplicationStats;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.notification.data.enums.NotificationTargetType;
import dev.hidly.nubi.notification.data.enums.NotificationType;
import dev.hidly.nubi.notification.service.NotificationService;
import dev.hidly.nubi.notification.service.NotificationText;
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
    private final NotificationService notificationService;

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

    @Transactional
    public GlobalResponse<EventApplicationDetailResponseDto> decide(Long eventId, Long applicationId,
                                                                   ApplicationDecisionRequestDto request) {
        Program event = getHostedEvent(eventId);
        Application application = getApplicationOf(eventId, applicationId);
        ApplicationStatus before = application.getStatus();

        if (request.getStatus() == ApplicationStatus.CONFIRMED && before != ApplicationStatus.CONFIRMED) {
            checkCapacityForConfirm(application);
        }
        application.decideByHost(request.getStatus());
        if (request.getReason() != null && !request.getReason().isBlank()) {
            application.addMessage(ApplicationMessage.builder()
                    .senderOrganization(event.getHostOrganization())
                    .body(request.getReason().trim())
                    .build());
        }
        applicationRepository.flush();
        notificationService.notify(application.getApplicantOrganization().getId(), NotificationType.APPLICATION_DECIDED,
                decisionTitle(application.getStatus()),
                NotificationText.body("%s 신청이 %s 상태가 되었어요.".formatted(event.getTitle(), decisionLabel(application.getStatus()))),
                NotificationTargetType.APPLICATION, application.getId());
        log.info("[decideApplication] 신청 상태 변경: eventId={}, applicationId={}, {} -> {}",
                eventId, applicationId, before, application.getStatus());

        return GlobalResponse.ok(toDetail(application));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<EventNeedsResponseDto> getNeeds(Long eventId) {
        Program event = getHostedEvent(eventId);
        List<Application> applications = applicationRepository.findByProgramIdAndStatusNotInOrderByIdAsc(
                eventId, ApplicationStatus.RELEASED);

        Map<Long, List<EventNeedsResponseDto.NeedBreakdown>> breakdownByTag = new LinkedHashMap<>();
        Map<Long, ApplicationSupport> tagSample = new LinkedHashMap<>();
        Map<Long, List<String>> notesByTag = new LinkedHashMap<>();
        List<EventNeedsResponseDto.CareNeed> cares = new ArrayList<>();
        List<EventNeedsResponseDto.Arrival> arrivals = new ArrayList<>();

        for (Application application : applications) {
            String orgName = application.getApplicantOrganization().getName();
            String groupName = application.getGroup() == null ? null : application.getGroup().getName();
            for (ApplicationSupport support : application.getSupports()) {
                Long tagId = support.getSupportTag().getId();
                int quantity = support.getQuantity() != null ? support.getQuantity() : application.getParticipantCount();
                tagSample.putIfAbsent(tagId, support);
                breakdownByTag.computeIfAbsent(tagId, id -> new ArrayList<>()).add(new EventNeedsResponseDto.NeedBreakdown(
                        application.getId(), support.getId(), orgName, groupName, quantity, support.getPrepStatus()));
                if (support.getHostNote() != null && !support.getHostNote().isBlank()) {
                    notesByTag.computeIfAbsent(tagId, id -> new ArrayList<>()).add(support.getHostNote());
                }
            }
            application.getCares().forEach(care -> cares.add(new EventNeedsResponseDto.CareNeed(
                    application.getId(), care.getAliasSnapshot(), orgName, care.getContentSnapshot())));
            arrivals.add(new EventNeedsResponseDto.Arrival(application.getId(), orgName, groupName,
                    application.getTransport(), application.getParticipantCount(), application.getCompanionCount(),
                    application.getStatus()));
        }

        List<EventNeedsResponseDto.NeedItem> items = breakdownByTag.entrySet().stream()
                .map(entry -> {
                    ApplicationSupport sample = tagSample.get(entry.getKey());
                    List<EventNeedsResponseDto.NeedBreakdown> rows = entry.getValue();
                    return new EventNeedsResponseDto.NeedItem(entry.getKey(), sample.getSupportTag().getCategory(),
                            sample.getSupportTag().getCode(), sample.getSupportTag().getLabel(),
                            rows.stream().mapToInt(EventNeedsResponseDto.NeedBreakdown::quantity).sum(),
                            combine(rows), rows, notesByTag.getOrDefault(entry.getKey(), List.of()));
                })
                .sorted(Comparator.comparingInt(EventNeedsResponseDto.NeedItem::totalQuantity).reversed()
                        .thenComparing(EventNeedsResponseDto.NeedItem::supportTagId))
                .toList();
        int ready = (int) items.stream().filter(item -> item.prepStatus() == PrepStatus.READY).count();

        EventApplicationStats stats = statsOf(List.of(eventId)).getOrDefault(eventId, EventApplicationStats.empty(eventId));
        return GlobalResponse.ok(new EventNeedsResponseDto(event.getCapacity(), stats.participantCount(),
                stats.companionCount(), stats.newApplicationCount(), ready, items, cares, arrivals));
    }

    private static String decisionTitle(ApplicationStatus status) {
        return switch (status) {
            case CONFIRMED -> "신청이 확정됐어요";
            case REJECTED -> "신청이 거절됐어요";
            default -> "신청이 대기 중이에요";
        };
    }

    private static String decisionLabel(ApplicationStatus status) {
        return switch (status) {
            case CONFIRMED -> "확정";
            case REJECTED -> "거절";
            default -> "대기";
        };
    }

    private static PrepStatus combine(List<EventNeedsResponseDto.NeedBreakdown> rows) {
        if (rows.stream().anyMatch(row -> row.prepStatus() == PrepStatus.CHECK)) {
            return PrepStatus.CHECK;
        }
        if (rows.stream().allMatch(row -> row.prepStatus() == PrepStatus.READY)) {
            return PrepStatus.READY;
        }
        return PrepStatus.PENDING;
    }

    // 지원 준비 체크 (API-46)
    @Transactional
    public GlobalResponse<EventApplicationDetailResponseDto> updateSupportPrep(Long eventId, Long applicationId,
                                                                              Long supportId, SupportPrepRequestDto request) {
        getHostedEvent(eventId);
        Application application = getApplicationOf(eventId, applicationId);
        ApplicationSupport support = application.getSupports().stream()
                .filter(s -> s.getId().equals(supportId))
                .findFirst()
                .orElseThrow(ApplicationSupportNotFoundException::new);
        support.updatePrep(request.getPrepStatus(), request.getHostNote());
        applicationRepository.flush();
        log.info("[updateSupportPrep] 지원 준비 체크: applicationId={}, supportId={}, {}",
                applicationId, supportId, request.getPrepStatus());
        return GlobalResponse.ok(toDetail(application));
    }

    // 답장 보내기 (API-47)
    @Transactional
    public GlobalResponse<EventApplicationDetailResponseDto> reply(Long eventId, Long applicationId, MessageRequestDto request) {
        Program event = getHostedEvent(eventId);
        Application application = getApplicationOf(eventId, applicationId);
        if (!application.acceptsMessages()) {
            throw new InvalidApplicationStatusException();
        }
        application.addMessage(ApplicationMessage.builder()
                .senderOrganization(event.getHostOrganization())
                .body(request.getBody().trim())
                .build());
        applicationRepository.flush();
        log.info("[replyApplication] 답장: eventId={}, applicationId={}", eventId, applicationId);
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

    private void checkCapacityForConfirm(Application application) {
        Program locked = programRepository.findLockedById(application.getProgram().getId())
                .orElseThrow(ProgramNotFoundException::new);
        if (locked.getCapacity() == null) {
            return;
        }
        long others = applicationRepository.sumParticipantsExceptApplication(locked.getId(),
                ApplicationStatus.NOT_HOLDING_SEAT, application.getId());
        if (others + application.getParticipantCount() > locked.getCapacity()) {
            throw new CapacityExceededException();
        }
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
