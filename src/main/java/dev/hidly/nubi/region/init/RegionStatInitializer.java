package dev.hidly.nubi.region.init;

import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.region.data.entity.RegionStat;
import dev.hidly.nubi.region.data.repository.RegionStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// 기획서 2장 공공데이터 집계값.
// 등록장애인: 경기도 장애인 등록현황(2025-12-31), 시설: 용인시 장애인복지시설 현황 43곳(2026-05-07)
// ponytail: 읍면동 행은 읍면동별 등록장애인 데이터를 받으면 추가
@Component
@RequiredArgsConstructor
public class RegionStatInitializer implements ApplicationRunner {
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 5, 7);

    private final RegionStatRepository regionStatRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!regionStatRepository.findAllByEmdCodeIsNull().isEmpty()) {
            return;
        }
        regionStatRepository.saveAll(List.of(
                district(District.CHEOIN, 23, 13_964),
                district(District.GIHEUNG, 17, 15_161),
                district(District.SUJI, 3, 9_081)));
    }

    private static RegionStat district(District district, int facilityCount, int registeredDisabled) {
        return RegionStat.builder()
                .district(district)
                .facilityCount(facilityCount)
                .registeredDisabled(registeredDisabled)
                .baseDate(BASE_DATE)
                .build();
    }
}
