package dev.hidly.nubi.program.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.program.data.dto.response.FacilityResponseDto;
import dev.hidly.nubi.program.data.dto.response.FacilitySourceResponseDto;
import dev.hidly.nubi.program.data.dto.response.VenueFacilitiesResponseDto;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.entity.VenueFacility;
import dev.hidly.nubi.program.data.enums.FacilityStatus;
import dev.hidly.nubi.program.data.enums.FacilityType;
import dev.hidly.nubi.program.data.exception.VenueNotFoundException;
import dev.hidly.nubi.program.data.repository.VenueFacilityRepository;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VenueService {
    private final VenueRepository venueRepository;
    private final VenueFacilityRepository venueFacilityRepository;

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

    private FacilityResponseDto merge(FacilityType type, List<VenueFacility> facilities) {
        FacilityStatus status = facilities.stream()
                .filter(facility -> facility.getStatus() != FacilityStatus.UNKNOWN)
                .max(Comparator.comparing(VenueFacility::getVerifiedAt, Comparator.nullsFirst(LocalDateTime::compareTo)))
                .map(VenueFacility::getStatus)
                .orElse(FacilityStatus.UNKNOWN);

        List<FacilitySourceResponseDto> sources = facilities.stream()
                .sorted(Comparator.comparing(VenueFacility::getSource))
                .map(FacilitySourceResponseDto::from)
                .toList();

        return new FacilityResponseDto(type, status, sources);
    }
}
