package dev.hidly.nubi.collect.service;

import dev.hidly.nubi.collect.facility.AccessibilityFacilityClient;
import dev.hidly.nubi.collect.facility.AccessibilityFacilityClient.Place;
import dev.hidly.nubi.collect.facility.AccessibilityFacilityMatcher;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.entity.VenueFacility;
import dev.hidly.nubi.program.data.enums.FacilitySource;
import dev.hidly.nubi.program.data.enums.FacilityStatus;
import dev.hidly.nubi.program.data.enums.FacilityType;
import dev.hidly.nubi.program.data.repository.VenueFacilityRepository;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 장애인편의시설 현황으로 우리 장소의 편의시설(venue_facility, 출처 PUBLIC_DATA)을 채운다.
// 공공데이터에 "설치됨"으로 나온 것만 YES로 넣는다. 목록에 없다고 없는 것은 아니라서 NO는 넣지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueFacilitySync {
    private static final String SIDO = "경기도";
    private static final String SIGUNGU = "용인시";

    private final AccessibilityFacilityClient client;
    private final VenueRepository venueRepository;
    private final VenueFacilityRepository venueFacilityRepository;
    private final TransactionTemplate transactionTemplate;

    public record Result(int venues, int matched, int facilities) {
    }

    public Result syncAll() {
        if (!client.isEnabled()) {
            return new Result(0, 0, 0);
        }
        List<Place> places = client.findPlaces(SIDO, SIGUNGU);
        if (places.isEmpty()) {
            log.warn("[VenueFacilitySync] 장애인편의시설 목록을 받지 못해 건너뜀");
            return new Result(0, 0, 0);
        }
        Map<String, List<Place>> index = AccessibilityFacilityMatcher.indexByAddress(places);
        List<Venue> venues = venueRepository.findAllByAddressIsNotNull();
        int matched = 0;
        int facilities = 0;
        for (Venue venue : venues) {
            Optional<Place> place = AccessibilityFacilityMatcher.match(venue.getName(), venue.getAddress(), index);
            if (place.isEmpty()) {
                // 전에 잘못 이어졌던 장소라면 공공데이터 편의시설을 지운다.
                replace(venue.getId(), Set.of());
                continue;
            }
            Set<FacilityType> types = AccessibilityFacilityMatcher.toFacilityTypes(client.findInstalledItems(place.get().id()));
            replace(venue.getId(), types);
            matched++;
            facilities += types.size();
            log.debug("[VenueFacilitySync] {} ↔ {} ({}): {}", venue.getName(), place.get().name(), place.get().address(), types);
        }
        log.info("[VenueFacilitySync] 주소 있는 장소 {}곳 중 {}곳을 장애인편의시설과 연결, 편의시설 {}건", venues.size(), matched, facilities);
        return new Result(venues.size(), matched, facilities);
    }

    private void replace(Long venueId, Set<FacilityType> types) {
        transactionTemplate.executeWithoutResult(status -> {
            venueFacilityRepository.deleteAllByVenueIdAndSource(venueId, FacilitySource.PUBLIC_DATA);
            Venue venue = venueRepository.getReferenceById(venueId);
            LocalDateTime now = LocalDateTime.now();
            venueFacilityRepository.saveAll(types.stream()
                    .map(type -> VenueFacility.builder()
                            .venue(venue)
                            .facilityType(type)
                            .status(FacilityStatus.YES)
                            .source(FacilitySource.PUBLIC_DATA)
                            .verifiedAt(now)
                            .build())
                    .toList());
        });
    }
}
