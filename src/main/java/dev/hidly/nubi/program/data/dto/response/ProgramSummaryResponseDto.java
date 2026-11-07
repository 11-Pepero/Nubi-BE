package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ApplyMethod;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import dev.hidly.nubi.program.data.enums.ProgramStatus;

import java.time.LocalDateTime;

public record ProgramSummaryResponseDto(
        Long programId,
        String title,
        ProgramCategory category,
        String hostName,
        ProgramStatus status,
        ApplyMethod applyMethod,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime deadlineAt,
        Integer capacity,
        String thumbnailUrl,
        VenueSummaryResponseDto venue
) {
    public static ProgramSummaryResponseDto of(Program program, String thumbnailUrl) {
        return new ProgramSummaryResponseDto(
                program.getId(),
                program.getTitle(),
                program.getCategory(),
                program.getDisplayHostName(),
                program.getStatus(),
                program.getApplyMethod(),
                program.getStartAt(),
                program.getEndAt(),
                program.getDeadlineAt(),
                program.getCapacity(),
                thumbnailUrl,
                VenueSummaryResponseDto.from(program.getVenue()));
    }
}
