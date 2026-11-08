package dev.hidly.nubi.collect;

import dev.hidly.nubi.collect.service.NoticeCollectService;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.program.data.repository.RawNoticeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {"external.kakao.rest-api-key=", "external.data-go-kr.service-key="})
@AutoConfigureMockMvc
class NoticeCollectServiceTest {
    @Autowired
    private NoticeCollectService noticeCollectService;
    @Autowired
    private RawNoticeRepository rawNoticeRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private MockMvc mockMvc;

    private static class FakeCollector implements NoticeCollector {
        private final List<NoticeData> notices;
        private final List<String> skipped = new ArrayList<>();

        FakeCollector(List<NoticeData> notices) {
            this.notices = notices;
        }

        @Override
        public String dataset() {
            return "TEST";
        }

        @Override
        public List<NoticeData> collect(Predicate<String> alreadyCollected) {
            return notices.stream().filter(notice -> {
                boolean known = alreadyCollected.test(notice.externalId());
                if (known) {
                    skipped.add(notice.externalId());
                }
                return !known;
            }).toList();
        }
    }

    private NoticeData notice(String title, LocalDateTime startAt, LocalDateTime endAt, String place, String address) {
        return new NoticeData("TEST", "TEST-" + UUID.randomUUID().toString().substring(0, 20), Map.of("title", title),
                title, ProgramCategory.CULTURE, startAt, endAt, place, address, "031-000-0000", "관람시간: 10시", "https://example.com/1");
    }

    private Program programOf(String externalId) {
        return transactionTemplate.execute(status -> programRepository.findAll().stream()
                .filter(p -> p.getRawNotice() != null && p.getRawNotice().getExternalId().equals(externalId))
                .peek(p -> { if (p.getVenue() != null) p.getVenue().getName(); })
                .findFirst().orElseThrow());
    }

    @Test
    void 수집한_공고를_원문과_프로그램으로_저장하고_장소를_연결한다() throws Exception {
        String word = "수집" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime start = LocalDateTime.now().plusDays(3);
        NoticeData upcoming = notice(word + " 어울림 음악회", start, start.plusHours(2), word + "홀", "경기도 용인시 수지구 포은대로 499");
        NoticeData past = notice(word + " 지난 전시", start.minusDays(30), start.minusDays(20), word + "홀", null);

        Map<String, Integer> result = noticeCollectService.collect(List.of(new FakeCollector(List.of(upcoming, past))));

        assertThat(result).containsEntry("TEST", 2);
        assertThat(rawNoticeRepository.existsByExternalId(upcoming.externalId())).isTrue();
        Program saved = programOf(upcoming.externalId());
        assertThat(saved.getStatus()).isEqualTo(ProgramStatus.OPEN);
        assertThat(saved.getTitle()).isEqualTo(word + " 어울림 음악회");
        assertThat(saved.getApplyUrl()).isEqualTo("https://example.com/1");
        assertThat(saved.getVenue().getName()).isEqualTo(word + "홀");
        assertThat(saved.getVenue().getDistrict()).isEqualTo(District.SUJI);
        assertThat(programOf(past.externalId()).getStatus()).isEqualTo(ProgramStatus.ENDED);
        assertThat(programOf(past.externalId()).getVenue().getId()).isEqualTo(saved.getVenue().getId());

        mockMvc.perform(get("/api/v1/programs").param("keyword", word))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].venue.district").value("SUJI"));
    }

    @Test
    void 같은_공고는_다시_넣지_않는다() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        NoticeData notice = notice("중복 확인", start, start.plusHours(1), "중복홀", null);
        FakeCollector collector = new FakeCollector(List.of(notice));

        noticeCollectService.collect(List.of(collector));
        Map<String, Integer> second = noticeCollectService.collect(List.of(collector));

        assertThat(second).containsEntry("TEST", 0);
        assertThat(collector.skipped).containsExactly(notice.externalId());
    }

    @Test
    void 끝난_수집_공고만_종료로_바꾸고_기관_행사는_건드리지_않는다() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(1);
        NoticeData soon = notice("곧 끝남", start.minusHours(2), start.minusSeconds(1).plusSeconds(1), "종료홀", null);
        noticeCollectService.collect(List.of(new FakeCollector(List.of(soon))));
        Program hostEvent = programRepository.save(Program.builder().title("기관 행사").status(ProgramStatus.OPEN)
                .startAt(start.minusDays(2)).endAt(start.minusDays(1)).build());

        transactionTemplate.executeWithoutResult(status -> programRepository.findById(programOf(soon.externalId()).getId())
                .ifPresent(p -> assertThat(p.getStatus()).isEqualTo(ProgramStatus.OPEN)));

        NoticeData pastNotice = notice("이미 끝남", start.minusDays(3), start.minusDays(2), "종료홀", null);
        noticeCollectService.collect(List.of(new FakeCollector(List.of(pastNotice))));

        assertThat(programOf(pastNotice.externalId()).getStatus()).isEqualTo(ProgramStatus.ENDED);
        assertThat(programRepository.findById(hostEvent.getId()).orElseThrow().getStatus()).isEqualTo(ProgramStatus.OPEN);
    }

    @Test
    void 한_수집기가_실패해도_다른_수집기는_계속한다() {
        NoticeCollector broken = new NoticeCollector() {
            @Override
            public String dataset() {
                return "BROKEN";
            }

            @Override
            public List<NoticeData> collect(Predicate<String> alreadyCollected) {
                throw new IllegalStateException("사이트 구조 변경");
            }
        };
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        Map<String, Integer> result = noticeCollectService.collect(List.of(broken,
                new FakeCollector(List.of(notice("정상 공고", start, start.plusHours(1), "정상홀", null)))));

        assertThat(result).containsEntry("BROKEN", -1).containsEntry("TEST", 1);
    }
}
