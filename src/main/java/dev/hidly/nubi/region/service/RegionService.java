package dev.hidly.nubi.region.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.repository.ProgramSpecifications;
import dev.hidly.nubi.region.data.dto.response.DistrictStatResponseDto;
import dev.hidly.nubi.region.data.dto.response.DistrictStatsResponseDto;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionService {
    private final RegionStatRepository regionStatRepository;

    @Transactional(readOnly = true)
    public GlobalResponse<DistrictStatsResponseDto> getDistricts(YearMonth month) {
        YearMonth target = month == null ? YearMonth.now() : month;
        LocalDateTime from = target.atDay(1).atStartOfDay();
        LocalDateTime to = target.plusMonths(1).atDay(1).atStartOfDay();

        Map<District, Long> programs = toMap(regionStatRepository.countProgramsByDistrict(
                ProgramSpecifications.SEARCHABLE_STATUSES, from, to));
        Map<District, Long> accessible = toMap(regionStatRepository.countAccessibleProgramsByDistrict(
                ProgramSpecifications.SEARCHABLE_STATUSES, ProgramSpecifications.ACCESSIBILITY_FACILITIES, from, to));

        List<DistrictStatResponseDto> districts = regionStatRepository.findAllByEmdCodeIsNull().stream()
                .sorted(Comparator.comparing(RegionStat::getDistrict))
                .map(stat -> toDto(stat, programs.getOrDefault(stat.getDistrict(), 0L),
                        accessible.getOrDefault(stat.getDistrict(), 0L)))
                .toList();
        return GlobalResponse.ok(new DistrictStatsResponseDto(target, districts));
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

    private static Map<District, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (District) row[0], row -> (Long) row[1]));
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
