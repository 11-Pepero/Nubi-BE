package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.application.data.repository.EventApplicationStats;
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
    // 신청 기관 수, 참여 인원(이용자), 새 신청 수는 거절·취소를 뺀 신청 기준
    public static EventSummaryResponseDto of(Program program, String thumbnailUrl, EventApplicationStats stats) {
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
                stats.applicantOrgCount().intValue(),
                stats.participantCount().intValue(),
                stats.newApplicationCount().intValue());
    }
}
