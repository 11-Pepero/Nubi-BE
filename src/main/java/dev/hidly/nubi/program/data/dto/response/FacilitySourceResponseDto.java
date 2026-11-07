package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.program.data.entity.VenueFacility;
import dev.hidly.nubi.program.data.enums.FacilitySource;
import dev.hidly.nubi.program.data.enums.FacilityStatus;

import java.time.LocalDateTime;

public record FacilitySourceResponseDto(FacilitySource source, FacilityStatus status, LocalDateTime verifiedAt) {
    public static FacilitySourceResponseDto from(VenueFacility facility) {
        return new FacilitySourceResponseDto(facility.getSource(), facility.getStatus(), facility.getVerifiedAt());
    }
}
