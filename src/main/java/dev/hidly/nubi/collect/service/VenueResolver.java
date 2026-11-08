package dev.hidly.nubi.collect.service;

import dev.hidly.nubi.collect.kakao.KakaoLocalClient;
import dev.hidly.nubi.common.geo.GeoPoints;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VenueResolver {
    private final VenueRepository venueRepository;
    private final KakaoLocalClient kakaoLocalClient;

    public Venue resolve(String name, String address) {
        String venueName = name == null || name.isBlank() ? address : name.trim();
        if (venueName == null || venueName.isBlank()) {
            return null;
        }
        String trimmedName = venueName.length() > 100 ? venueName.substring(0, 100) : venueName;

        Venue venue = venueRepository.findFirstByNameOrderByIdAsc(trimmedName)
                .orElseGet(() -> venueRepository.save(Venue.builder()
                        .name(trimmedName)
                        .address(address)
                        .district(District.fromAddress(address).orElse(null))
                        .build()));

        fillLocation(venue);
        return venue;
    }

    public int fillMissingLocations() {
        if (!kakaoLocalClient.isEnabled()) {
            return 0;
        }
        return (int) venueRepository.findTop50ByLocationIsNullOrderByIdAsc().stream()
                .filter(this::fillLocation)
                .count();
    }

    private boolean fillLocation(Venue venue) {
        if (venue.getLocation() != null) {
            return false;
        }
        String query = venue.getAddress() != null ? venue.getAddress() : "용인 " + venue.getName();
        return kakaoLocalClient.search(query).map(place -> {
            venue.fillLocation(place.address(), District.fromAddress(place.address()).orElse(null),
                    GeoPoints.of(place.latitude(), place.longitude()));
            return true;
        }).orElse(false);
    }
}
