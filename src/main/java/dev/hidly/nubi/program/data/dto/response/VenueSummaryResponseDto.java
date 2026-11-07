package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Venue;

public record VenueSummaryResponseDto(Long venueId, String name, District district) {
    public static VenueSummaryResponseDto from(Venue venue) {
        return venue == null ? null : new VenueSummaryResponseDto(venue.getId(), venue.getName(), venue.getDistrict());
    }
}
