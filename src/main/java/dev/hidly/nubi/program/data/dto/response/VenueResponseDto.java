package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Venue;

public record VenueResponseDto(Long venueId, String name, String address, District district,
                               Double latitude, Double longitude) {
    public static VenueResponseDto from(Venue venue) {
        if (venue == null) {
            return null;
        }
        return new VenueResponseDto(venue.getId(), venue.getName(), venue.getAddress(), venue.getDistrict(),
                venue.getLocation() == null ? null : venue.getLocation().getY(),
                venue.getLocation() == null ? null : venue.getLocation().getX());
    }
}
