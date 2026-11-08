package dev.hidly.nubi.application.data.dto.response;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.enums.ApplicationStep;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ApplyMethod;

import java.time.LocalDateTime;

// 내 신청 목록(화면 05)의 한 줄
public record MyApplicationSummaryResponseDto(
        Long applicationId,
        Long programId,
        String programTitle,
        String hostName,
        LocalDateTime startAt,
        String venueName,
        ApplyMethod applyMethod,
        String applyUrl,
        String groupName,
        int participantCount,
        int companionCount,
        ApplyMethod channel,
        ApplicationStatus status,
        ApplicationStep step,
        LocalDateTime submittedAt
) {
    public static MyApplicationSummaryResponseDto from(Application application) {
        Program program = application.getProgram();
        return new MyApplicationSummaryResponseDto(
                application.getId(),
                program.getId(),
                program.getTitle(),
                program.getDisplayHostName(),
                program.getStartAt(),
                program.getVenue() == null ? null : program.getVenue().getName(),
                program.getApplyMethod(),
                program.getApplyUrl(),
                application.getGroup() == null ? null : application.getGroup().getName(),
                application.getParticipantCount(),
                application.getCompanionCount(),
                application.getChannel(),
                application.getStatus(),
                ApplicationStep.of(application.getStatus()),
                application.getSubmittedAt());
    }
}
