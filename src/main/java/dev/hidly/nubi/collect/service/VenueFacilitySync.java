package dev.hidly.nubi.collect.service;

import dev.hidly.nubi.collect.facility.AccessibilityFacilityClient;
import dev.hidly.nubi.collect.facility.AccessibilityFacilityClient.Place;
import dev.hidly.nubi.collect.facility.AccessibilityFacilityMatcher;
import dev.hidly.nubi.collect.facility.BarrierFreeMatcher;
import dev.hidly.nubi.collect.facility.BarrierFreeTourClient;
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
import org.locationtech.jts.geom.Point;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

// 장애인편의시설 현황과 한국관광공사 무장애 여행 정보로 우리 장소의 편의시설(venue_facility, 출처 PUBLIC_DATA)을 채운다.
// 무장애 여행 정보에는 장애인편의시설 현황에 없는 공연장·박물관 같은 문화시설이 있다.
// 공공데이터에 "설치됨"으로 나온 것만 YES로 넣는다. 목록에 없다고 없는 것은 아니라서 NO는 넣지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueFacilitySync {
    private static final String SIDO = "경기도";
    private static final String SIGUNGU = "용인시";

    private final AccessibilityFacilityClient client;
    private final BarrierFreeTourClient barrierFreeClient;
    private final VenueRepository venueRepository;
    private final VenueFacilityRepository venueFacilityRepository;
    private final TransactionTemplate transactionTemplate;

    public record Result(int venues, int matched, int facilities) {
    }

    // 두 출처의 용인시 목록. 한쪽이라도 켜져 있는데 목록을 못 받으면 null (일부만으로 덮어써 편의시설이 지워지지 않게)
    private record Sources(Map<String, List<Place>> index, List<BarrierFreeTourClient.Place> tourPlaces) {
    }

    // 새로 등록한 장소 하나의 편의시설은 요청을 기다리게 하지 않으려고 따로 채운다.
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

    public Result syncAll() {
        Sources sources = loadSources();
        if (sources == null) {
            return new Result(0, 0, 0);
        }
        List<Venue> venues = venueRepository.findAllByAddressIsNotNull();
        int matched = 0;
        int facilities = 0;
        for (Venue venue : venues) {
            Optional<Set<FacilityType>> types = apply(venue, sources);
            if (types.isPresent()) {
                matched++;
                facilities += types.get().size();
            }
        }
        log.info("[VenueFacilitySync] 주소 있는 장소 {}곳 중 {}곳을 장애인편의시설·무장애 여행 정보와 연결, 편의시설 {}건",
                venues.size(), matched, facilities);
        return new Result(venues.size(), matched, facilities);
    }

    // 장소 고르기로 새로 등록한 장소의 편의시설을 뒤에서 채운다. 실패해도 장소 등록에는 영향이 없다.
    public void syncVenueLater(Long venueId) {
        background.execute(() -> {
            try {
                syncVenue(venueId);
            } catch (RuntimeException e) {
                log.warn("[VenueFacilitySync] 장소 {} 편의시설 채우기 실패", venueId, e);
            }
        });
    }

    public Optional<Set<FacilityType>> syncVenue(Long venueId) {
        Venue venue = venueRepository.findById(venueId).orElse(null);
        if (venue == null || venue.getAddress() == null) {
            return Optional.empty();
        }
        Sources sources = loadSources();
        Optional<Set<FacilityType>> types = sources == null ? Optional.empty() : apply(venue, sources);
        log.info("[VenueFacilitySync] 장소 {} ({}) 편의시설 채우기: {}", venue.getId(), venue.getName(),
                types.map(Object::toString).orElse("이어지는 공공데이터 없음"));
        return types;
    }

    private Sources loadSources() {
        if (!client.isEnabled() && !barrierFreeClient.isEnabled()) {
            return null;
        }
        List<Place> places = client.isEnabled() ? client.findPlaces(SIDO, SIGUNGU) : List.of();
        if (client.isEnabled() && places.isEmpty()) {
            log.warn("[VenueFacilitySync] 장애인편의시설 목록을 받지 못해 건너뜀");
            return null;
        }
        List<BarrierFreeTourClient.Place> tourPlaces = barrierFreeClient.isEnabled()
                ? barrierFreeClient.findYonginPlaces() : List.of();
        if (barrierFreeClient.isEnabled() && tourPlaces.isEmpty()) {
            log.warn("[VenueFacilitySync] 무장애 여행 정보 목록을 받지 못해 건너뜀");
            return null;
        }
        return new Sources(AccessibilityFacilityMatcher.indexByAddress(places), tourPlaces);
    }

    // 두 출처에서 "있음"으로 나온 것을 합친다. 어느 쪽과도 안 이어지면 전에 잘못 이어졌던 편의시설을 지우고 비어 있는 값을 준다.
    private Optional<Set<FacilityType>> apply(Venue venue, Sources sources) {
        Set<FacilityType> types = EnumSet.noneOf(FacilityType.class);
        Optional<Place> place = AccessibilityFacilityMatcher.match(venue.getName(), venue.getAddress(), sources.index());
        place.ifPresent(found -> {
            types.addAll(AccessibilityFacilityMatcher.toFacilityTypes(client.findInstalledItems(found.id())));
            log.debug("[VenueFacilitySync] {} ↔ 장애인편의시설 {} ({})", venue.getName(), found.name(), found.address());
        });
        Point location = venue.getLocation();
        Optional<BarrierFreeTourClient.Place> tourPlace = BarrierFreeMatcher.match(venue.getName(), venue.getAddress(),
                location == null ? null : location.getY(), location == null ? null : location.getX(), sources.tourPlaces());
        tourPlace.ifPresent(found -> {
            types.addAll(BarrierFreeMatcher.toFacilityTypes(barrierFreeClient.findDetail(found.contentId())));
            log.debug("[VenueFacilitySync] {} ↔ 무장애 여행 정보 {} ({})", venue.getName(), found.title(), found.address());
        });
        replace(venue.getId(), types);
        if (place.isEmpty() && tourPlace.isEmpty()) {
            return Optional.empty();
        }
        log.debug("[VenueFacilitySync] {}: {}", venue.getName(), types);
        return Optional.of(types);
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
