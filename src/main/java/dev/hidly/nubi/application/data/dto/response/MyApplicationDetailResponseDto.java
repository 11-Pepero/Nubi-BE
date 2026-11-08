package dev.hidly.nubi.application.data.dto.response;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.enums.ApplicationStep;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ApplyMethod;

import java.time.LocalDateTime;
import java.util.List;

// 신청 상세(API-31): 신청한 기관이 보는 화면. 신청 서류(application_document)는 C 파트 AI 서류가 생기면 붙인다.
public record MyApplicationDetailResponseDto(
        Long applicationId,
        ApplicationStatus status,
        ApplicationStep step,
        ApplyMethod channel,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,
        ProgramInfo program,
        String groupName,
        int participantCount,
        int companionCount,
        String transport,
        List<ApplicationResponseDto.SupportSnapshot> supports,
        List<ApplicationResponseDto.CareSnapshot> cares,
        List<EventApplicationDetailResponseDto.MessageResponseDto> messages
) {
    public record ProgramInfo(Long programId, String title, String hostName, LocalDateTime startAt,
                              LocalDateTime endAt, String venueName, String venueAddress, ApplyMethod applyMethod,
                              String applyUrl, String contactPhone) {
        static ProgramInfo from(Program program) {
            return new ProgramInfo(program.getId(), program.getTitle(), program.getDisplayHostName(),
                    program.getStartAt(), program.getEndAt(),
                    program.getVenue() == null ? null : program.getVenue().getName(),
                    program.getVenue() == null ? null : program.getVenue().getAddress(),
                    program.getApplyMethod(), program.getApplyUrl(), program.getContactPhone());
        }
    }

    public static MyApplicationDetailResponseDto from(Application application) {
        Program program = application.getProgram();
        Long hostId = program.getHostOrganization() == null ? null : program.getHostOrganization().getId();
        return new MyApplicationDetailResponseDto(
                application.getId(),
                application.getStatus(),
                ApplicationStep.of(application.getStatus()),
                application.getChannel(),
                application.getSubmittedAt(),
                application.getDecidedAt(),
                ProgramInfo.from(program),
                application.getGroup() == null ? null : application.getGroup().getName(),
                application.getParticipantCount(),
                application.getCompanionCount(),
                application.getTransport(),
                application.getSupports().stream().map(ApplicationResponseDto.SupportSnapshot::from).toList(),
                application.getCares().stream().map(ApplicationResponseDto.CareSnapshot::from).toList(),
                application.getMessages().stream()
                        .map(message -> EventApplicationDetailResponseDto.MessageResponseDto.of(message, hostId))
                        .toList());
    }
}
