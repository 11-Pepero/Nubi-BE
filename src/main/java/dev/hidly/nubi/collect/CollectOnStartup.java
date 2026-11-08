package dev.hidly.nubi.collect;

import dev.hidly.nubi.collect.service.NoticeCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "collect", name = "run-on-startup", havingValue = "true")
public class CollectOnStartup implements ApplicationRunner {
    private final NoticeCollectService noticeCollectService;

    @Override
    public void run(ApplicationArguments args) {
        noticeCollectService.collectAll();
    }
}
