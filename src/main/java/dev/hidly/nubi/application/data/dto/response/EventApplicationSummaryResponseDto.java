package dev.hidly.nubi.application.data.dto.response;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.organization.data.entity.Member;

import java.time.LocalDateTime;
import java.util.List;

// 신청 기관 목록(화면 12)의 한 줄
public record EventApplicationSummaryResponseDto(
        Long applicationId,
        Long organizationId,
        String organizationName,
        String groupName,
        String contactName,
        int participantCount,
        int companionCount,
        List<ApplicationResponseDto.SupportSnapshot> supports,
        int careCount,
        ApplicationStatus status,
        LocalDateTime submittedAt
) {
    public static EventApplicationSummaryResponseDto of(Application application, Member contact) {
        return new EventApplicationSummaryResponseDto(
                application.getId(),
                application.getApplicantOrganization().getId(),
                application.getApplicantOrganization().getName(),
                application.getGroup() == null ? null : application.getGroup().getName(),
                contact == null ? null : contact.getName(),
                application.getParticipantCount(),
                application.getCompanionCount(),
                application.getSupports().stream().map(ApplicationResponseDto.SupportSnapshot::from).toList(),
                application.getCares().size(),
                application.getStatus(),
                application.getSubmittedAt());
    }
}
