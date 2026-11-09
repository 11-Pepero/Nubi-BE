package dev.hidly.nubi.application.service;

import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 행사가 끝난 확정 신청을 "다녀왔어요(ATTENDED)"로 바꾼다. 그래야 신청 현황의 다녀왔어요 탭과 후기 단계로 넘어간다.
// 행사 상태(ENDED)는 ProgramStatusService가 따로 바꾼다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationStatusScheduler {
    private final ApplicationRepository applicationRepository;

    @Transactional
    @Scheduled(cron = "${application.attended-cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void markAttended() {
        markAttended(LocalDateTime.now());
    }

    @Transactional
    public int markAttended(LocalDateTime now) {
        int attended = applicationRepository.markAttendedFinished(now);
        if (attended > 0) {
            log.info("[ApplicationStatusScheduler] 다녀왔어요로 변경: {}건", attended);
        }
        return attended;
    }
}
