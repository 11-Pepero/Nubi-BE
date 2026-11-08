package dev.hidly.nubi.program.service;

import dev.hidly.nubi.program.data.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramStatusService {
    private final ProgramRepository programRepository;

    @Transactional
    @Scheduled(cron = "${program.status-cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void refresh() {
        LocalDateTime now = LocalDateTime.now();
        int ended = programRepository.endFinished(now);
        int closed = programRepository.closePastDeadline(now);
        if (ended > 0 || closed > 0) {
            log.info("[ProgramStatusService] 상태 갱신: 종료 {}건, 마감 {}건", ended, closed);
        }
    }
}
