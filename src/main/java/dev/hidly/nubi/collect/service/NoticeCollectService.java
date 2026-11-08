package dev.hidly.nubi.collect.service;

import dev.hidly.nubi.collect.NoticeCollector;
import dev.hidly.nubi.collect.NoticeData;
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

    public Map<String, Integer> collectAll() {
        return collect(collectors);
    }

    public Map<String, Integer> collect(List<NoticeCollector> targets) {
        Map<String, Integer> savedByDataset = new LinkedHashMap<>();
        for (NoticeCollector collector : targets) {
            try {
                List<NoticeData> notices = collector.collect(noticeStore::exists);
                savedByDataset.put(collector.dataset(), noticeStore.save(notices));
            } catch (RuntimeException e) {
                log.error("[NoticeCollectService] {} 수집 실패", collector.dataset(), e);
                savedByDataset.put(collector.dataset(), -1);
            }
        }
        int located = noticeStore.fillMissingLocations();
        int ended = noticeStore.endExpired();
        log.info("[NoticeCollectService] 공고 수집 완료: 새 공고 {}, 좌표 채운 장소 {}곳, 종료 처리 {}건", savedByDataset, located, ended);
        return savedByDataset;
    }
}
