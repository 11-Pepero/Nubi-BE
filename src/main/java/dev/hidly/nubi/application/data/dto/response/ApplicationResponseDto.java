package dev.hidly.nubi.application.data.dto.response;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.entity.ApplicationCare;
import dev.hidly.nubi.application.data.entity.ApplicationSupport;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.enums.PrepStatus;
import dev.hidly.nubi.program.data.enums.ApplyMethod;
import dev.hidly.nubi.support.data.enums.SupportCategory;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationResponseDto(
        Long applicationId,
        Long programId,
        String programTitle,
        Long groupId,
        String groupName,
        int participantCount,
        int companionCount,
        String transport,
        ApplyMethod channel,
        ApplicationStatus status,
        LocalDateTime submittedAt,
        List<SupportSnapshot> supports,
        List<CareSnapshot> cares
) {
    public record SupportSnapshot(Long applicationSupportId, Long supportTagId, SupportCategory category, String code,
                                  String label, Integer quantity, PrepStatus prepStatus, String hostNote) {
        static SupportSnapshot from(ApplicationSupport support) {
            return new SupportSnapshot(support.getId(), support.getSupportTag().getId(),
                    support.getSupportTag().getCategory(), support.getSupportTag().getCode(),
                    support.getSupportTag().getLabel(), support.getQuantity(), support.getPrepStatus(),
                    support.getHostNote());
        }
    }

    public record CareSnapshot(Long carePersonId, String alias, String content) {
        static CareSnapshot from(ApplicationCare care) {
            return new CareSnapshot(care.getCarePerson() == null ? null : care.getCarePerson().getId(),
                    care.getAliasSnapshot(), care.getContentSnapshot());
        }
    }

    public static ApplicationResponseDto from(Application application) {
        return new ApplicationResponseDto(
                application.getId(),
                application.getProgram().getId(),
                application.getProgram().getTitle(),
                application.getGroup() == null ? null : application.getGroup().getId(),
                application.getGroup() == null ? null : application.getGroup().getName(),
                application.getParticipantCount(),
                application.getCompanionCount(),
                application.getTransport(),
                application.getChannel(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getSupports().stream().map(SupportSnapshot::from).toList(),
                application.getCares().stream().map(CareSnapshot::from).toList());
    }
}
