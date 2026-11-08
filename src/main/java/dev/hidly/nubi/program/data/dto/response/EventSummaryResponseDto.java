package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import dev.hidly.nubi.program.data.enums.ProgramStatus;

import java.time.LocalDateTime;

public record EventSummaryResponseDto(
        Long eventId,
        String title,
        ProgramCategory category,
        ProgramStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime deadlineAt,
        Integer capacity,
        String thumbnailUrl,
        VenueSummaryResponseDto venue,
        int applicationOrgCount,
        int participantCount,
        int newApplicationCount
) {
    // 신청 기관 수, 참여 인원, 새 신청 수는 신청(application) 도메인이 생기면 채운다. 지금은 0.
    public static EventSummaryResponseDto of(Program program, String thumbnailUrl) {
        return new EventSummaryResponseDto(
                program.getId(),
                program.getTitle(),
                program.getCategory(),
                program.getStatus(),
                program.getStartAt(),
                program.getEndAt(),
                program.getDeadlineAt(),
                program.getCapacity(),
                thumbnailUrl,
                VenueSummaryResponseDto.from(program.getVenue()),
                0,
                0,
                0);
    }
}
