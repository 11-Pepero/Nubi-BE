package dev.hidly.nubi.support.init;

import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.enums.SupportCategory;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static dev.hidly.nubi.support.data.enums.SupportCategory.COMMUNICATION;
import static dev.hidly.nubi.support.data.enums.SupportCategory.COMPANION;
import static dev.hidly.nubi.support.data.enums.SupportCategory.ENVIRONMENT;
import static dev.hidly.nubi.support.data.enums.SupportCategory.MEAL;
import static dev.hidly.nubi.support.data.enums.SupportCategory.MOBILITY;
import static dev.hidly.nubi.support.data.enums.SupportCategory.REST;
import static dev.hidly.nubi.support.data.enums.SupportCategory.TIME;
import static dev.hidly.nubi.support.data.enums.SupportCategory.TOILET;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportTagInitializer implements ApplicationRunner {
    private static final List<Seed> SEEDS = List.of(
            new Seed(MOBILITY, "WHEELCHAIR", "휠체어"),
            new Seed(MOBILITY, "POWER_WHEELCHAIR", "전동휠체어"),
            new Seed(MOBILITY, "WALKING_AID", "보행 보조"),
            new Seed(MOBILITY, "NO_STAIRS", "계단 없는 이동"),
            new Seed(TOILET, "ACCESSIBLE_TOILET", "장애인 화장실"),
            new Seed(TOILET, "TOILET_ASSIST", "화장실 동행"),
            new Seed(COMPANION, "COMPANION", "동반 인력"),
            new Seed(COMPANION, "ONE_TO_ONE", "1:1 전담"),
            new Seed(COMMUNICATION, "EASY_WORDS", "쉬운 말 안내"),
            new Seed(COMMUNICATION, "SIGN_LANGUAGE", "수어 통역"),
            new Seed(COMMUNICATION, "PICTURE_GUIDE", "그림 안내"),
            new Seed(ENVIRONMENT, "QUIET_SPACE", "조용한 공간"),
            new Seed(ENVIRONMENT, "LOW_STIMULUS", "소리, 빛 자극 줄이기"),
            new Seed(MEAL, "MEAL_ASSIST", "식사 보조"),
            new Seed(MEAL, "SPECIAL_DIET", "식단 조절"),
            new Seed(REST, "REST_AREA", "휴식 공간"),
            new Seed(TIME, "SHORT_SESSION", "짧은 진행 시간"),
            new Seed(TIME, "FLEXIBLE_TIME", "유연한 참여 시간")
    );

    private final SupportTagRepository supportTagRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SupportTag> missing = SEEDS.stream()
                .filter(seed -> !supportTagRepository.existsByCode(seed.code()))
                .map(seed -> SupportTag.builder()
                        .category(seed.category())
                        .code(seed.code())
                        .label(seed.label())
                        .build())
                .toList();

        if (!missing.isEmpty()) {
            supportTagRepository.saveAll(missing);
            log.info("[SupportTagInitializer] 지원 항목 {}개 추가", missing.size());
        }
    }

    private record Seed(SupportCategory category, String code, String label) {
    }
}
