package dev.hidly.nubi.region.service;

import dev.hidly.nubi.collect.kakao.KakaoLocalClient;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 좌표는 있는데 행정동 코드가 빈 장소를 채워 읍면동별 프로그램 수를 셀 수 있게 한다.
// ponytail: 카카오가 끝내 못 찾는 장소는 매번 다시 시도한다. 쌓이면 시도 시각을 기록해 건너뛴다.
@Component
@RequiredArgsConstructor
public class VenueEmdFiller {
    private final VenueRepository venueRepository;
    private final KakaoLocalClient kakaoLocalClient;

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    @Transactional
    public int fill() {
        if (!kakaoLocalClient.isEnabled()) {
            return 0;
        }
        int filled = 0;
        for (Venue venue : venueRepository.findTop100ByLocationIsNotNullAndEmdCodeIsNullOrderByIdAsc()) {
            var code = kakaoLocalClient.findAdministrativeDongCode(venue.getLocation().getY(), venue.getLocation().getX());
            if (code.isPresent()) {
                venue.fillEmdCode(code.get());
                filled++;
            }
        }
        return filled;
    }
}
