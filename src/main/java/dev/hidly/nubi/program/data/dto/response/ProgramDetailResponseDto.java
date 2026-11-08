package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ApplyMethod;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import dev.hidly.nubi.program.data.enums.ProgramStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ProgramDetailResponseDto(
        Long programId,
        String title,
        ProgramCategory category,
        String hostName,
        Long hostOrganizationId,
        String description,
        ProgramStatus status,
        ApplyMethod applyMethod,
        String applyUrl,
        String contactPhone,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime deadlineAt,
        Integer capacity,
        List<ProgramImageResponseDto> images,
        VenueResponseDto venue,
        List<FacilityResponseDto> facilities,
        EligibilityResponseDto eligibility
) {
    public static ProgramDetailResponseDto of(Program program, List<ProgramImageResponseDto> images,
                                              List<FacilityResponseDto> facilities) {
        return of(program, images, facilities, null);
    }

    public static ProgramDetailResponseDto of(Program program, List<ProgramImageResponseDto> images,
                                              List<FacilityResponseDto> facilities, EligibilityResponseDto eligibility) {
        return new ProgramDetailResponseDto(
                program.getId(),
                program.getTitle(),
                program.getCategory(),
                program.getDisplayHostName(),
                program.getHostOrganization() == null ? null : program.getHostOrganization().getId(),
                program.getDescription(),
                program.getStatus(),
                program.getApplyMethod(),
                program.getApplyUrl(),
                program.getContactPhone(),
                program.getStartAt(),
                program.getEndAt(),
                program.getDeadlineAt(),
                program.getCapacity(),
                images,
                VenueResponseDto.from(program.getVenue()),
                facilities,
                eligibility);
    }
}
