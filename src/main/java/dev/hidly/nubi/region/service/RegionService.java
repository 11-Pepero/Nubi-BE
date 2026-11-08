package dev.hidly.nubi.region.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.repository.ProgramSpecifications;
import dev.hidly.nubi.region.data.DisabilityFacilities;
import dev.hidly.nubi.region.data.dto.response.MarkersResponseDto;
import dev.hidly.nubi.region.data.dto.response.DistrictDetailResponseDto;
import dev.hidly.nubi.region.data.dto.response.DistrictStatResponseDto;
import dev.hidly.nubi.region.data.dto.response.DistrictStatsResponseDto;
import dev.hidly.nubi.region.data.dto.response.EmdStatResponseDto;
import dev.hidly.nubi.region.data.entity.RegionStat;
import dev.hidly.nubi.region.data.repository.RegionStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionService {
    private final RegionStatRepository regionStatRepository;
    private final DisabilityFacilities disabilityFacilities;

    private record Period(YearMonth month, LocalDateTime from, LocalDateTime to) {
        static Period of(YearMonth month) {
            YearMonth target = month == null ? YearMonth.now() : month;
            return new Period(target, target.atDay(1).atStartOfDay(), target.plusMonths(1).atDay(1).atStartOfDay());
        }
    }

    @Transactional(readOnly = true)
    public GlobalResponse<DistrictStatsResponseDto> getDistricts(YearMonth month) {
        Period period = Period.of(month);
        Map<District, Long> programs = toMap(regionStatRepository.countProgramsByDistrict(
                ProgramSpecifications.SEARCHABLE_STATUSES, period.from(), period.to()));
        Map<District, Long> accessible = toMap(regionStatRepository.countAccessibleProgramsByDistrict(
                ProgramSpecifications.SEARCHABLE_STATUSES, ProgramSpecifications.ACCESSIBILITY_FACILITIES, period.from(), period.to()));

        List<DistrictStatResponseDto> districts = regionStatRepository.findAllByEmdCodeIsNull().stream()
                .sorted(Comparator.comparing(RegionStat::getDistrict))
                .map(stat -> toDto(stat, programs.getOrDefault(stat.getDistrict(), 0L),
                        accessible.getOrDefault(stat.getDistrict(), 0L)))
                .toList();
        return GlobalResponse.ok(new DistrictStatsResponseDto(period.month(), districts));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<DistrictDetailResponseDto> getDistrict(District district, YearMonth month) {
        Period period = Period.of(month);
        DistrictStatResponseDto summary = getDistricts(period.month()).getData().districts().stream()
                .filter(stat -> stat.district() == district)
                .findFirst()
                .orElseThrow();

        Map<String, Long> programs = toMap(regionStatRepository.countProgramsByEmd(
                ProgramSpecifications.SEARCHABLE_STATUSES, period.from(), period.to()));
        Map<String, Long> accessible = toMap(regionStatRepository.countAccessibleProgramsByEmd(
                ProgramSpecifications.SEARCHABLE_STATUSES, ProgramSpecifications.ACCESSIBILITY_FACILITIES, period.from(), period.to()));

        List<EmdStatResponseDto> emds = regionStatRepository.findAllByDistrictAndEmdCodeIsNotNull(district).stream()
                .map(stat -> new EmdStatResponseDto(stat.getEmdCode(), stat.getEmdName(), stat.getFacilityCount(),
                        stat.getRegisteredDisabled(), programs.getOrDefault(stat.getEmdCode(), 0L),
                        accessible.getOrDefault(stat.getEmdCode(), 0L)))
                .sorted(Comparator.comparingLong(EmdStatResponseDto::programCount)
                        .thenComparing(Comparator.comparingInt(EmdStatResponseDto::facilityCount).reversed())
                        .thenComparing(EmdStatResponseDto::emdName))
                .toList();
        return GlobalResponse.ok(new DistrictDetailResponseDto(period.month(), summary, emds));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<MarkersResponseDto> getMarkers(District district, YearMonth month) {
        Period period = Period.of(month);
        List<MarkersResponseDto.FacilityMarker> facilities = disabilityFacilities.all().stream()
                .filter(facility -> district == null || facility.district() == district)
                .map(facility -> new MarkersResponseDto.FacilityMarker(facility.name(), facility.facilityType(),
                        facility.district(), facility.emdName(), facility.latitude(), facility.longitude()))
                .toList();

        List<Program> programs = regionStatRepository.findMappablePrograms(
                        ProgramSpecifications.SEARCHABLE_STATUSES, period.from(), period.to()).stream()
                .filter(program -> district == null || program.getVenue().getDistrict() == district)
                .toList();
        Set<Long> venueIds = programs.stream().map(program -> program.getVenue().getId()).collect(Collectors.toSet());
        Set<Long> accessibleVenues = venueIds.isEmpty() ? Set.of() : Set.copyOf(
                regionStatRepository.findAccessibleVenueIds(venueIds, ProgramSpecifications.ACCESSIBILITY_FACILITIES));
        List<MarkersResponseDto.ProgramMarker> programMarkers = programs.stream()
                .map(program -> {
                    Venue venue = program.getVenue();
                    return new MarkersResponseDto.ProgramMarker(program.getId(), program.getTitle(), program.getStartAt(),
                            venue.getName(), venue.getDistrict(), accessibleVenues.contains(venue.getId()),
                            venue.getLocation().getY(), venue.getLocation().getX());
                })
                .toList();
        return GlobalResponse.ok(new MarkersResponseDto(period.month(), facilities, programMarkers));
    }

    private static DistrictStatResponseDto toDto(RegionStat stat, long programCount, long accessibleProgramCount) {
        double facilitiesPerThousand = stat.getRegisteredDisabled() == 0
                ? 0 : stat.getFacilityCount() * 1000.0 / stat.getRegisteredDisabled();
        Double programsPerFacility = stat.getFacilityCount() == 0
                ? null : round1((double) programCount / stat.getFacilityCount());
        return new DistrictStatResponseDto(stat.getDistrict(), stat.getDistrict().getLabel(),
                stat.getFacilityCount(), stat.getRegisteredDisabled(), round1(facilitiesPerThousand),
                programCount, accessibleProgramCount, programsPerFacility);
    }

    @SuppressWarnings("unchecked")
    private static <K> Map<K, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (K) row[0], row -> (Long) row[1]));
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
