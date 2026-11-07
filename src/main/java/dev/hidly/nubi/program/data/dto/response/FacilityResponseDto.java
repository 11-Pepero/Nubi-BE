package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.program.data.enums.FacilityStatus;
import dev.hidly.nubi.program.data.enums.FacilityType;

import java.util.List;

public record FacilityResponseDto(FacilityType facilityType, FacilityStatus status, List<FacilitySourceResponseDto> sources) {
}
