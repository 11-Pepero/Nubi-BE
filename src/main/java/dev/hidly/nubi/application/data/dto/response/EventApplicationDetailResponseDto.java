package dev.hidly.nubi.application.data.dto.response;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.entity.ApplicationMessage;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.program.data.enums.ApplyMethod;

import java.time.LocalDateTime;
import java.util.List;

// 신청 내용 상세(화면 13 오른쪽 패널)
public record EventApplicationDetailResponseDto(
        Long applicationId,
        ApplicationStatus status,
        ApplyMethod channel,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,
        Long organizationId,
        String organizationName,
        String organizationPhone,
        ContactResponseDto contact,
        String groupName,
        int participantCount,
        int companionCount,
        String transport,
        List<ApplicationResponseDto.SupportSnapshot> supports,
        List<ApplicationResponseDto.CareSnapshot> cares,
        List<MessageResponseDto> messages
) {
    public record MessageResponseDto(Long messageId, Long senderOrganizationId, String senderOrganizationName,
                                     boolean fromHost, String body, LocalDateTime createdAt) {
        static MessageResponseDto of(ApplicationMessage message, Long hostOrganizationId) {
            Long senderId = message.getSenderOrganization().getId();
            return new MessageResponseDto(message.getId(), senderId, message.getSenderOrganization().getName(),
                    senderId.equals(hostOrganizationId), message.getBody(), message.getCreatedAt());
        }
    }

    public static EventApplicationDetailResponseDto of(Application application, Member contact) {
        Long hostId = application.getProgram().getHostOrganization() == null ? null
                : application.getProgram().getHostOrganization().getId();
        return new EventApplicationDetailResponseDto(
                application.getId(),
                application.getStatus(),
                application.getChannel(),
                application.getSubmittedAt(),
                application.getDecidedAt(),
                application.getApplicantOrganization().getId(),
                application.getApplicantOrganization().getName(),
                application.getApplicantOrganization().getPhone(),
                ContactResponseDto.from(contact),
                application.getGroup() == null ? null : application.getGroup().getName(),
                application.getParticipantCount(),
                application.getCompanionCount(),
                application.getTransport(),
                application.getSupports().stream().map(ApplicationResponseDto.SupportSnapshot::from).toList(),
                application.getCares().stream().map(ApplicationResponseDto.CareSnapshot::from).toList(),
                application.getMessages().stream().map(message -> MessageResponseDto.of(message, hostId)).toList());
    }
}
