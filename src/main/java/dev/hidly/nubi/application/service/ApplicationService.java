package dev.hidly.nubi.application.service;

import dev.hidly.nubi.application.data.dto.request.DirectApplicationRequestDto;
import dev.hidly.nubi.application.data.dto.request.PrepareApplicationRequestDto;
import dev.hidly.nubi.application.data.dto.request.SubmittedRequestDto;
import dev.hidly.nubi.application.data.dto.response.ApplicationResponseDto;
import dev.hidly.nubi.application.data.dto.response.MyApplicationDetailResponseDto;
import dev.hidly.nubi.application.data.dto.response.PreparedApplicationResponseDto;
import dev.hidly.nubi.application.data.exception.ApplicationAccessDeniedException;
import dev.hidly.nubi.application.data.exception.ApplicationNotFoundException;
import dev.hidly.nubi.application.data.exception.InvalidApplicationRequestException;
import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.entity.ApplicationCare;
import dev.hidly.nubi.application.data.entity.ApplicationMessage;
import dev.hidly.nubi.application.data.entity.ApplicationSupport;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.exception.ApplicationDuplicatedException;
import dev.hidly.nubi.application.data.exception.ApplicationNotAllowedException;
import dev.hidly.nubi.application.data.exception.CapacityExceededException;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.notification.data.enums.NotificationTargetType;
import dev.hidly.nubi.notification.data.enums.NotificationType;
import dev.hidly.nubi.notification.service.NotificationService;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ApplyMethod;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.exception.ProgramNotFoundException;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.support.data.entity.CareItem;
import dev.hidly.nubi.support.data.entity.CarePerson;
import dev.hidly.nubi.support.data.entity.SupportGroup;
import dev.hidly.nubi.support.data.exception.CarePersonAccessDeniedException;
import dev.hidly.nubi.support.data.exception.CarePersonNotFoundException;
import dev.hidly.nubi.support.data.exception.GroupAccessDeniedException;
import dev.hidly.nubi.support.data.exception.GroupNotFoundException;
import dev.hidly.nubi.support.data.repository.CarePersonRepository;
import dev.hidly.nubi.support.data.repository.SupportGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final ApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;
    private final OrganizationRepository organizationRepository;
    private final SupportGroupRepository supportGroupRepository;
    private final CarePersonRepository carePersonRepository;
    private final NotificationService notificationService;

    @Transactional
    public GlobalResponse<ApplicationResponseDto> applyDirect(Long programId, DirectApplicationRequestDto request) {
        Long organizationId = SecurityUtil.getOrganizationId();
        Program program = programRepository.findLockedById(programId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(ProgramNotFoundException::new);
        validateDirectApplicable(program, organizationId);

        SupportGroup group = getOwnedGroup(request.getGroupId(), organizationId);
        List<CarePerson> carePersons = getOwnedCarePersons(request.careIdsOrEmpty(), organizationId);
        int participantCount = request.getParticipantCount() != null ? request.getParticipantCount() : group.getMemberCount();
        int companionCount = request.getCompanionCount() != null ? request.getCompanionCount() : 0;

        if (applicationRepository.existsByProgramIdAndGroupIdAndStatusIn(programId, group.getId(), ApplicationStatus.IN_PROGRESS)) {
            throw new ApplicationDuplicatedException();
        }
        if (program.getCapacity() != null) {
            long taken = applicationRepository.sumParticipantsExcept(programId, ApplicationStatus.NOT_HOLDING_SEAT);
            if (taken + participantCount > program.getCapacity()) {
                throw new CapacityExceededException();
            }
        }

        Organization applicant = organizationRepository.getReferenceById(organizationId);
        Application application = Application.direct(program, applicant, group, participantCount, companionCount,
                trimToNull(request.getTransport()));
        copySnapshots(application, group, carePersons);
        String message = trimToNull(request.getMessage());
        if (message != null) {
            application.addMessage(ApplicationMessage.builder().senderOrganization(applicant).body(message).build());
        }

        applicationRepository.save(application);
        if (program.getHostOrganization() != null) {
            notificationService.notify(program.getHostOrganization().getId(), NotificationType.NEW_APPLICATION,
                    "새 신청이 들어왔어요",
                    NotificationText.body("%s에 %s %d명이 신청했어요.".formatted(program.getTitle(), applicant.getName(), participantCount)),
                    NotificationTargetType.APPLICATION, application.getId());
        }
        log.info("[applyDirect] 바로 신청: organizationId={}, programId={}, applicationId={}, 인원 {}명, 개별 케어 {}명",
                organizationId, programId, application.getId(), participantCount, carePersons.size());

        return GlobalResponse.ok(ApplicationResponseDto.from(application));
    }

    // 외부 공고 신청 준비: 앱은 대신 접수하지 않고, 준비 중(PREPARING) 신청과 접수 채널만 만들어 준다.
    @Transactional
    public GlobalResponse<PreparedApplicationResponseDto> prepare(PrepareApplicationRequestDto request) {
        Long organizationId = SecurityUtil.getOrganizationId();
        Program program = programRepository.findById(request.getProgramId())
                .filter(p -> !p.isDeleted())
                .orElseThrow(ProgramNotFoundException::new);
        validatePreparable(program, organizationId);

        SupportGroup group = getOwnedGroup(request.getGroupId(), organizationId);
        List<CarePerson> carePersons = getOwnedCarePersons(request.careIdsOrEmpty(), organizationId);
        if (applicationRepository.existsByProgramIdAndGroupIdAndStatusIn(program.getId(), group.getId(), ApplicationStatus.IN_PROGRESS)) {
            throw new ApplicationDuplicatedException();
        }
        int participantCount = request.getParticipantCount() != null ? request.getParticipantCount() : group.getMemberCount();
        int companionCount = request.getCompanionCount() != null ? request.getCompanionCount() : 0;

        Application application = Application.prepare(program, organizationRepository.getReferenceById(organizationId),
                group, participantCount, companionCount);
        copySnapshots(application, group, carePersons);
        applicationRepository.save(application);
        log.info("[prepareApplication] 신청 준비: organizationId={}, programId={}, applicationId={}, 채널 {}",
                organizationId, program.getId(), application.getId(), program.getApplyMethod());

        return GlobalResponse.ok(PreparedApplicationResponseDto.of(application));
    }

    // 담당자가 전화·온라인·서면으로 직접 접수한 뒤 누르는 "접수 완료" (PREPARING → SUBMITTED)
    @Transactional
    public GlobalResponse<MyApplicationDetailResponseDto> markSubmitted(Long applicationId, SubmittedRequestDto request) {
        Application application = applicationRepository.findWithProgramById(applicationId)
                .orElseThrow(ApplicationNotFoundException::new);
        if (!application.getApplicantOrganization().getId().equals(SecurityUtil.getOrganizationId())) {
            throw new ApplicationAccessDeniedException();
        }
        LocalDateTime submittedAt = request.getSubmittedAt() != null ? request.getSubmittedAt() : LocalDateTime.now();
        if (submittedAt.isAfter(LocalDateTime.now().plusMinutes(1)) || request.getChannel() == ApplyMethod.DIRECT) {
            throw new InvalidApplicationRequestException();
        }
        application.markSubmitted(request.getChannel(), submittedAt);
        applicationRepository.flush();
        log.info("[markSubmitted] 접수 완료: applicationId={}, channel={}", applicationId, application.getChannel());

        return GlobalResponse.ok(MyApplicationDetailResponseDto.from(application));
    }

    private void validatePreparable(Program program, Long organizationId) {
        LocalDateTime now = LocalDateTime.now();
        boolean directOrUnknown = program.getApplyMethod() == null || program.getApplyMethod() == ApplyMethod.DIRECT;
        boolean notOpen = program.getStatus() != ProgramStatus.OPEN;
        boolean ownProgram = program.isHostedBy(organizationId);
        boolean pastDeadline = program.getDeadlineAt() != null && now.isAfter(program.getDeadlineAt());
        if (directOrUnknown || notOpen || ownProgram || pastDeadline) {
            throw new ApplicationNotAllowedException();
        }
    }

    private void copySnapshots(Application application, SupportGroup group, List<CarePerson> carePersons) {
        group.getSupports().forEach(support -> application.addSupport(ApplicationSupport.builder()
                .supportTag(support.getSupportTag())
                .quantity(support.getQuantity())
                .build()));
        carePersons.forEach(care -> application.addCare(ApplicationCare.builder()
                .carePerson(care)
                .aliasSnapshot(care.getAlias())
                .contentSnapshot(describe(care))
                .build()));
    }

    private void validateDirectApplicable(Program program, Long organizationId) {
        LocalDateTime now = LocalDateTime.now();
        boolean notOpenEvent = program.getStatus() != ProgramStatus.OPEN || program.getApplyMethod() != ApplyMethod.DIRECT;
        boolean ownEvent = program.isHostedBy(organizationId);
        boolean pastDeadline = program.getDeadlineAt() != null && now.isAfter(program.getDeadlineAt());
        boolean started = program.getStartAt() != null && now.isAfter(program.getStartAt());
        if (notOpenEvent || ownEvent || pastDeadline || started) {
            throw new ApplicationNotAllowedException();
        }
    }

    private SupportGroup getOwnedGroup(Long groupId, Long organizationId) {
        SupportGroup group = supportGroupRepository.findActiveWithSupportsById(groupId)
                .orElseThrow(GroupNotFoundException::new);
        if (!group.isOwnedBy(organizationId)) {
            throw new GroupAccessDeniedException();
        }
        return group;
    }

    private List<CarePerson> getOwnedCarePersons(List<Long> careIds, Long organizationId) {
        if (careIds.isEmpty()) {
            return List.of();
        }
        List<Long> uniqueIds = List.copyOf(new LinkedHashSet<>(careIds));
        Map<Long, CarePerson> found = carePersonRepository.findAllById(uniqueIds).stream()
                .filter(care -> care.getDeletedAt() == null)
                .collect(Collectors.toMap(CarePerson::getId, Function.identity()));
        if (found.size() != uniqueIds.size()) {
            throw new CarePersonNotFoundException();
        }
        if (found.values().stream().anyMatch(care -> !care.isOwnedBy(organizationId))) {
            throw new CarePersonAccessDeniedException();
        }
        return uniqueIds.stream().map(found::get).toList();
    }

    // 케어 항목을 "지원 항목: 내용" 한 줄씩 복사한다. 메모는 개인 정보가 섞일 수 있어 복사하지 않는다.
    private static String describe(CarePerson care) {
        return care.getItems().stream()
                .map(ApplicationService::describe)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private static String describe(CareItem item) {
        String label = item.getSupportTag() == null ? null : item.getSupportTag().getLabel();
        String content = trimToNull(item.getContent());
        if (label == null) {
            return content == null ? "" : content;
        }
        return content == null ? label : label + ": " + content;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
