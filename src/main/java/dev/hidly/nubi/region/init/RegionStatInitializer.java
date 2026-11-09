package dev.hidly.nubi.region.init;

import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.region.data.DisabilityFacilities;
import dev.hidly.nubi.region.data.entity.RegionStat;
import dev.hidly.nubi.region.data.repository.RegionStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 공공데이터로 구·읍면동 행을 처음 한 번 넣는다.
// 등록장애인: 용인시 연령 및 유형별 등록장애인 현황(2025-12-31), 읍면동 39곳(이동면은 이동읍에 합치고 양지면은 양지읍으로)
// 시설 수: 용인시 장애인복지시설 현황 43곳(2026-05-07)
@Component
@RequiredArgsConstructor
public class RegionStatInitializer implements ApplicationRunner {
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 5, 7);

    private final RegionStatRepository regionStatRepository;
    private final DisabilityFacilities disabilityFacilities;

    private record EmdRow(District district, String emdCode, String emdName, int registeredDisabled) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        List<EmdRow> emds = new ClassPathResource("data/region/emd_stats.csv").getContentAsString(StandardCharsets.UTF_8)
                .lines().skip(1).filter(line -> !line.isBlank()).map(line -> line.split(","))
                .map(c -> new EmdRow(District.valueOf(c[0]), c[1], c[2], Integer.parseInt(c[3])))
                .toList();
        Map<String, Long> facilitiesByEmd = disabilityFacilities.all().stream()
                .collect(Collectors.groupingBy(DisabilityFacilities.Facility::emdCode, Collectors.counting()));
        Map<District, Long> facilitiesByDistrict = disabilityFacilities.all().stream()
                .collect(Collectors.groupingBy(DisabilityFacilities.Facility::district, Collectors.counting()));

        List<RegionStat> rows = new ArrayList<>();
        if (regionStatRepository.findAllByEmdCodeIsNull().isEmpty()) {
            for (District district : District.values()) {
                int registered = emds.stream().filter(e -> e.district() == district).mapToInt(EmdRow::registeredDisabled).sum();
                rows.add(stat(district, null, null, facilitiesByDistrict.getOrDefault(district, 0L), registered));
            }
        }
        if (!regionStatRepository.existsByEmdCodeIsNotNull()) {
            for (EmdRow e : emds) {
                rows.add(stat(e.district(), e.emdCode(), e.emdName(), facilitiesByEmd.getOrDefault(e.emdCode(), 0L),
                        e.registeredDisabled()));
            }
        }
        regionStatRepository.saveAll(rows);
    }

    private static RegionStat stat(District district, String emdCode, String emdName, long facilityCount, int registeredDisabled) {
        return RegionStat.builder()
                .district(district)
                .emdCode(emdCode)
                .emdName(emdName)
                .facilityCount((int) facilityCount)
                .registeredDisabled(registeredDisabled)
                .baseDate(BASE_DATE)
                .build();
    }
}
