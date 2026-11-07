package dev.hidly.nubi.program.data.dto.response;

import java.util.List;

public record VenueFacilitiesResponseDto(Long venueId, String name, List<FacilityResponseDto> facilities) {
}
