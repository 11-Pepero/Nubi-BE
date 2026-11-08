package dev.hidly.nubi.region.data.dto.response;

import dev.hidly.nubi.organization.data.enums.District;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

public record MarkersResponseDto(YearMonth month, List<FacilityMarker> facilities, List<ProgramMarker> programs) {
    public record FacilityMarker(String name, String facilityType, District district, String emdName,
                                 double latitude, double longitude) {
    }

    public record ProgramMarker(Long programId, String title, LocalDateTime startAt, String venueName, District district,
                                boolean accessible, double latitude, double longitude) {
    }
}
