package dev.hidly.nubi.collect.service;

import dev.hidly.nubi.collect.NoticeCollector;
import dev.hidly.nubi.collect.NoticeData;
import dev.hidly.nubi.program.service.ProgramStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeCollectService {
    private final List<NoticeCollector> collectors;
    private final NoticeStore noticeStore;
    private final VenueResolver venueResolver;
    private final ProgramStatusService programStatusService;

    public Map<String, Integer> collectAll() {
        return collect(collectors);
    }

    public Map<String, Integer> collect(List<NoticeCollector> targets) {
        Map<String, Integer> savedByDataset = new LinkedHashMap<>();
        for (NoticeCollector collector : targets) {
            List<NoticeData> notices;
            try {
                notices = collector.collect(noticeStore::exists);
            } catch (RuntimeException e) {
                log.error("[NoticeCollectService] {} 수집 실패", collector.dataset(), e);
                savedByDataset.put(collector.dataset(), -1);
                continue;
            }
            int saved = 0;
            for (NoticeData notice : notices) {
                try {
                    if (noticeStore.save(notice)) {
                        saved++;
                    }
                } catch (RuntimeException e) {
                    log.warn("[NoticeCollectService] 공고 저장 실패: {} ({})", notice.externalId(), e.getMessage());
                }
            }
            savedByDataset.put(collector.dataset(), saved);
        }
        int located = venueResolver.fillMissingLocations();
        programStatusService.refresh();
        log.info("[NoticeCollectService] 공고 수집 완료: 새 공고 {}, 좌표 채운 장소 {}곳", savedByDataset, located);
        return savedByDataset;
    }
}
