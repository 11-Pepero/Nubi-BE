package dev.hidly.nubi.program.service;

import dev.hidly.nubi.collect.kakao.KakaoLocalClient.PlaceCandidate;
import dev.hidly.nubi.collect.kakao.KakaoLocalClient;
import dev.hidly.nubi.collect.service.VenueFacilitySync;
import dev.hidly.nubi.common.geo.GeoPoints;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.dto.request.VenueCreateRequestDto;
import dev.hidly.nubi.program.data.dto.response.FacilityResponseDto;
import dev.hidly.nubi.program.data.dto.response.FacilitySourceResponseDto;
import dev.hidly.nubi.program.data.dto.response.PlaceCandidateResponseDto;
import dev.hidly.nubi.program.data.dto.response.VenueFacilitiesResponseDto;
import dev.hidly.nubi.program.data.dto.response.VenueResponseDto;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.entity.VenueFacility;
import dev.hidly.nubi.program.data.enums.FacilityStatus;
import dev.hidly.nubi.program.data.enums.FacilityType;
import dev.hidly.nubi.program.data.exception.VenueNotFoundException;
import dev.hidly.nubi.program.data.repository.VenueFacilityRepository;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VenueService {
    private final VenueRepository venueRepository;
    private final VenueFacilityRepository venueFacilityRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final VenueFacilitySync venueFacilitySync;

    private static final int PLACE_CANDIDATES = 15;

    // 행사 등록 화면에서 장소를 고를 때 쓴다. 키워드가 없으면 전체를 이름순으로 준다.
    @Transactional(readOnly = true)
    public GlobalResponse<PageResponse<VenueResponseDto>> search(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name", "id"));
        Page<Venue> venues = keyword == null || keyword.isBlank()
                ? venueRepository.findAll(pageable)
                : venueRepository.search("%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%", pageable);
        return GlobalResponse.ok(PageResponse.from(venues.map(VenueResponseDto::from)));
    }

    // 행사 등록의 장소 고르기: 카카오에서 용인시 장소를 찾는다. 이미 우리 장소로 등록된 곳은 venueId를 같이 준다.
    @Transactional(readOnly = true)
    public GlobalResponse<List<PlaceCandidateResponseDto>> searchPlaces(String query) {
        List<PlaceCandidate> places = kakaoLocalClient.searchPlaces(query, PLACE_CANDIDATES);
        if (places.isEmpty()) {
            return GlobalResponse.ok(List.of());
        }
        // 이름이 같고 주소도 같은(또는 우리 쪽 주소가 비어 있는) 장소를 같은 곳으로 본다
        Map<String, List<Venue>> byName = venueRepository.findAllByNameIn(
                        places.stream().map(PlaceCandidate::name).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.groupingBy(Venue::getName));
        return GlobalResponse.ok(places.stream()
                .map(place -> PlaceCandidateResponseDto.of(place, byName.getOrDefault(place.name(), List.of()).stream()
                        .filter(venue -> venue.getAddress() == null || sameAddress(venue.getAddress(), place.displayAddress())
                                || sameAddress(venue.getAddress(), place.address()))
                        .map(Venue::getId)
                        .findFirst()
                        .orElse(null)))
                .toList());
    }

    // 카카오 장소 검색에서 고른 장소를 우리 장소로 등록한다. 같은 장소가 있으면 그 장소에 빠진 주소·좌표를 채워 돌려준다.
    // 장애인편의시설·무장애 여행 정보 편의시설은 등록이 끝난 뒤 따로 채운다.
    @Transactional
    public GlobalResponse<VenueResponseDto> register(VenueCreateRequestDto request) {
        String name = request.getName().trim();
        String address = request.getAddress().trim();
        Point location = GeoPoints.of(request.getLatitude(), request.getLongitude());
        District district = District.fromAddress(address).orElse(null);

        // 이름이 같고 주소가 같은 장소("경기도"·"경기" 차이와 띄어쓰기는 무시), 없으면 이름만 있고 주소가 빈 장소
        List<Venue> sameName = venueRepository.findAllByNameIn(List.of(name));
        Venue venue = sameName.stream().filter(v -> v.getAddress() != null && sameAddress(v.getAddress(), address)).findFirst()
                .or(() -> sameName.stream().filter(v -> v.getAddress() == null).findFirst())
                .orElse(null);
        if (venue == null) {
            venue = venueRepository.save(Venue.builder().name(name).address(address).district(district).location(location).build());
        } else {
            venue.fillLocation(address, district, location);
        }
        if (venue.getEmdCode() == null) {
            kakaoLocalClient.findAdministrativeDongCode(request.getLatitude(), request.getLongitude()).ifPresent(venue::fillEmdCode);
        }
        venue.markLocationChecked();

        Long venueId = venue.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                venueFacilitySync.syncVenueLater(venueId);
            }
        });
        return GlobalResponse.ok(VenueResponseDto.from(venue));
    }

    // "경기도 용인시 …"와 "경기 용인시 …"처럼 시도 표기와 띄어쓰기만 다른 주소를 같게 본다.
    static boolean sameAddress(String a, String b) {
        return a != null && b != null && normalizeAddress(a).equals(normalizeAddress(b));
    }

    private static String normalizeAddress(String address) {
        return address.trim().replaceFirst("^경기도", "경기").replaceAll("\\s+", "");
    }

    // like의 %, _ 를 글자로 찾도록 지운다. 이스케이프 문자 지정까지는 필요 없다.
    private static String escapeLike(String keyword) {
        return keyword.replace("%", "").replace("_", "");
    }

    @Transactional(readOnly = true)
    public GlobalResponse<VenueFacilitiesResponseDto> getFacilities(Long venueId) {
        Venue venue = venueRepository.findById(venueId).orElseThrow(VenueNotFoundException::new);
        return GlobalResponse.ok(new VenueFacilitiesResponseDto(venue.getId(), venue.getName(), mergeFacilities(venueId)));
    }

    @Transactional(readOnly = true)
    public List<FacilityResponseDto> mergeFacilities(Long venueId) {
        Map<FacilityType, List<VenueFacility>> byType = venueFacilityRepository.findAllByVenueId(venueId).stream()
                .collect(Collectors.groupingBy(VenueFacility::getFacilityType));

        return Arrays.stream(FacilityType.values())
                .map(type -> merge(type, byType.getOrDefault(type, List.of())))
                .toList();
    }

    public Map<Long, Map<FacilityType, FacilityStatus>> mergeStatusesByVenue(Collection<Long> venueIds) {
        if (venueIds.isEmpty()) {
            return Map.of();
        }
        return venueFacilityRepository.findAllByVenueIdIn(venueIds).stream()
                .collect(Collectors.groupingBy(facility -> facility.getVenue().getId(),
                        Collectors.collectingAndThen(Collectors.groupingBy(VenueFacility::getFacilityType),
                                byType -> byType.entrySet().stream()
                                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> mergeStatus(entry.getValue()))))));
    }

    private FacilityResponseDto merge(FacilityType type, List<VenueFacility> facilities) {
        FacilityStatus status = mergeStatus(facilities);

        List<FacilitySourceResponseDto> sources = facilities.stream()
                .sorted(Comparator.comparing(VenueFacility::getSource))
                .map(FacilitySourceResponseDto::from)
                .toList();

        return new FacilityResponseDto(type, status, sources);
    }

    private FacilityStatus mergeStatus(List<VenueFacility> facilities) {
        return facilities.stream()
                .filter(facility -> facility.getStatus() != FacilityStatus.UNKNOWN)
                .max(Comparator.comparing(VenueFacility::getVerifiedAt, Comparator.nullsFirst(LocalDateTime::compareTo)))
                .map(VenueFacility::getStatus)
                .orElse(FacilityStatus.UNKNOWN);
    }
}
