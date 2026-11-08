package dev.hidly.nubi.collect;

import dev.hidly.nubi.collect.service.NoticeCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "collect", name = "enabled", havingValue = "true")
public class CollectScheduler {
    private final NoticeCollectService noticeCollectService;

    @Scheduled(cron = "${collect.cron}", zone = "Asia/Seoul")
    public void collectDaily() {
        noticeCollectService.collectAll();
    }
}
