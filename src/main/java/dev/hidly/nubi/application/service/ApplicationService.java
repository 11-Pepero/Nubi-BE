package dev.hidly.nubi.application.service;

import dev.hidly.nubi.application.data.dto.request.DirectApplicationRequestDto;
import dev.hidly.nubi.application.data.dto.response.ApplicationResponseDto;
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

    // ponytail: 주최 기관 알림(NEW_APPLICATION)은 C 파트 알림 서비스가 생기면 여기서 보낸다.
    @Transactional
    public GlobalResponse<ApplicationResponseDto> applyDirect(Long programId, DirectApplicationRequestDto request) {
        Long organizationId = SecurityUtil.getOrganizationId();
        Program program = programRepository.findById(programId)
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
            long taken = applicationRepository.sumParticipantsExcept(programId, ApplicationStatus.RELEASED);
            if (taken + participantCount > program.getCapacity()) {
                throw new CapacityExceededException();
            }
        }

        Organization applicant = organizationRepository.getReferenceById(organizationId);
        Application application = Application.direct(program, applicant, group, participantCount, companionCount,
                trimToNull(request.getTransport()));
        group.getSupports().forEach(support -> application.addSupport(ApplicationSupport.builder()
                .supportTag(support.getSupportTag())
                .quantity(support.getQuantity())
                .build()));
        carePersons.forEach(care -> application.addCare(ApplicationCare.builder()
                .carePerson(care)
                .aliasSnapshot(care.getAlias())
                .contentSnapshot(describe(care))
                .build()));
        String message = trimToNull(request.getMessage());
        if (message != null) {
            application.addMessage(ApplicationMessage.builder().senderOrganization(applicant).body(message).build());
        }

        applicationRepository.save(application);
        log.info("[applyDirect] 바로 신청: organizationId={}, programId={}, applicationId={}, 인원 {}명, 개별 케어 {}명",
                organizationId, programId, application.getId(), participantCount, carePersons.size());

        return GlobalResponse.ok(ApplicationResponseDto.from(application));
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
