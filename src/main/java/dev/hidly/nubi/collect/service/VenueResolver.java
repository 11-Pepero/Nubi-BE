package dev.hidly.nubi.collect.service;

import dev.hidly.nubi.collect.kakao.KakaoLocalClient;
import dev.hidly.nubi.collect.kakao.KakaoLocalClient.Place;
import dev.hidly.nubi.common.geo.GeoPoints;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VenueResolver {
    private static final int LOCATION_BATCH = 50;

    private final VenueRepository venueRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final TransactionTemplate transactionTemplate;

    public Venue resolve(String name, String address) {
        String venueName = truncate(name == null || name.isBlank() ? address : name.trim(), 100);
        if (venueName == null || venueName.isBlank()) {
            return null;
        }
        String venueAddress = truncate(address, 200);
        return venueRepository.findFirstByNameOrderByIdAsc(venueName)
                .orElseGet(() -> venueRepository.save(Venue.builder()
                        .name(venueName)
                        .address(venueAddress)
                        .district(District.fromAddress(venueAddress).orElse(null))
                        .build()));
    }

    public int fillMissingLocations() {
        if (!kakaoLocalClient.isEnabled()) {
            return 0;
        }
        List<Venue> candidates = transactionTemplate.execute(status ->
                venueRepository.findWithoutLocation(PageRequest.of(0, LOCATION_BATCH)));
        int filled = 0;
        for (Venue candidate : candidates == null ? List.<Venue>of() : candidates) {
            String query = candidate.getAddress() != null ? candidate.getAddress() : "용인 " + candidate.getName();
            Optional<Place> place = kakaoLocalClient.search(query);
            Boolean located = transactionTemplate.execute(status -> venueRepository.findById(candidate.getId())
                    .map(venue -> {
                        venue.markLocationChecked();
                        place.ifPresent(found -> venue.fillLocation(truncate(found.address(), 200),
                                District.fromAddress(found.address()).orElse(null),
                                GeoPoints.of(found.latitude(), found.longitude())));
                        return place.isPresent();
                    })
                    .orElse(false));
            if (Boolean.TRUE.equals(located)) {
                filled++;
            }
        }
        return filled;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
