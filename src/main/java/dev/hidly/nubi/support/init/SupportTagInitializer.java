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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.hidly.nubi.support.data.enums.SupportCategory.COMMUNICATION;
import static dev.hidly.nubi.support.data.enums.SupportCategory.COMPANION;
import static dev.hidly.nubi.support.data.enums.SupportCategory.ENVIRONMENT;
import static dev.hidly.nubi.support.data.enums.SupportCategory.MEAL;
import static dev.hidly.nubi.support.data.enums.SupportCategory.MOBILITY;
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
            new Seed(MOBILITY, "VEHICLE_ASSIST", "차량 승하차 지원"),
            new Seed(MOBILITY, "PUBLIC_TRANSIT", "대중교통 이동 가능"),
            new Seed(MOBILITY, "NO_STAIRS", "계단 없는 이동"),
            new Seed(TOILET, "ACCESSIBLE_TOILET", "장애인 화장실"),
            new Seed(TOILET, "TOILET_ASSIST", "화장실 동행"),
            new Seed(COMPANION, "COMPANION", "동반 인력"),
            new Seed(COMPANION, "ONE_TO_ONE", "1:1 전담"),
            new Seed(COMMUNICATION, "EASY_WORDS", "쉬운 말 안내"),
            new Seed(COMMUNICATION, "PICTURE_BOARD", "그림 안내판"),
            new Seed(COMMUNICATION, "SIGN_LANGUAGE", "수어 통역"),
            new Seed(ENVIRONMENT, "QUIET_REST_SPACE", "조용한 휴식 공간"),
            new Seed(ENVIRONMENT, "LOW_STIMULUS", "소리, 빛 자극 줄이기"),
            new Seed(MEAL, "MEAL_ASSIST", "식사 보조"),
            new Seed(MEAL, "SPECIAL_DIET", "식단 조절"),
            new Seed(TIME, "WITHIN_90_MINUTES", "90분 이내 활동"),
            new Seed(TIME, "FLEXIBLE_TIME", "유연한 참여 시간")
    );

    private final SupportTagRepository supportTagRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, SupportTag> existing = supportTagRepository.findAll().stream()
                .collect(Collectors.toMap(SupportTag::getCode, Function.identity()));

        List<SupportTag> missing = new ArrayList<>();
        int updated = 0;
        for (Seed seed : SEEDS) {
            SupportTag tag = existing.get(seed.code());
            if (tag == null) {
                missing.add(SupportTag.builder()
                        .category(seed.category())
                        .code(seed.code())
                        .label(seed.label())
                        .build());
            } else if (tag.update(seed.category(), seed.label())) {
                updated++;
            }
        }

        supportTagRepository.saveAll(missing);
        if (!missing.isEmpty() || updated > 0) {
            log.info("[SupportTagInitializer] 지원 항목 추가 {}개, 수정 {}개", missing.size(), updated);
        }
    }

    private record Seed(SupportCategory category, String code, String label) {
    }
}
