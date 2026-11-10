package dev.hidly.nubi.collect.publicdata;

import dev.hidly.nubi.collect.NoticeData;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YonginProgramDataCollectorTest {

    @Test
    void 기간_글에서_날짜를_읽고_연도가_없으면_접수_연도를_쓴다() {
        assertThat(YonginOdcloudCollector.datesIn("2019-04-02 ~ 2019-06-27", null))
                .containsExactly(LocalDate.of(2019, 4, 2), LocalDate.of(2019, 6, 27));
        assertThat(YonginOdcloudCollector.datesIn("2018. 03. 20. ~ 2018. 06. 28.", null))
                .containsExactly(LocalDate.of(2018, 3, 20), LocalDate.of(2018, 6, 28));
        assertThat(YonginOdcloudCollector.datesIn("5월3일 ~ 5월 27일(매주 월,목요일) 11:00~12:00", 2021))
                .containsExactly(LocalDate.of(2021, 5, 3), LocalDate.of(2021, 5, 27));
        assertThat(YonginOdcloudCollector.datesIn("매주 화요일", 2021)).isEmpty();
    }

    @Test
    void 원문의_시험용_행을_알아본다() {
        assertThat(YonginOdcloudCollector.isTestRow("test")).isTrue();
        assertThat(YonginOdcloudCollector.isTestRow("테스트입니다.")).isTrue();
        assertThat(YonginOdcloudCollector.isTestRow("시험")).isTrue();
        assertThat(YonginOdcloudCollector.isTestRow("시험 대비 학습법 특강")).isFalse();
        assertThat(YonginOdcloudCollector.isTestRow("TEST 영어 회화")).isFalse();
    }

    @Test
    void 보건교육_공고를_보건소와_교육_기간으로_바꾼다() {
        Map<String, Object> row = new HashMap<>(Map.of(
                "교육일련번호", "85", "보건교육제목", "5월 <임산부 홈 필라테스 교실>참여자 모집",
                "보건교육기간", "5월3일 ~ 5월 27일(매주 월,목요일) 11:00~12:00",
                "보건교육접수시작일", "2021-04-16", "보건교육접수종료일", "2021-04-26",
                "교육 인원수", "29", "보건소지역코드", 4146300000L));

        NoticeData notice = new YonginHealthEducationCollector(null, "key").toNotice(row);

        assertThat(notice.externalId()).isEqualTo("YONGIN_HEALTH_EDUCATION-85");
        assertThat(notice.category()).isEqualTo(ProgramCategory.HEALTH);
        assertThat(notice.placeName()).isEqualTo("기흥구보건소");
        assertThat(notice.startAt()).isEqualTo(LocalDateTime.of(2021, 5, 3, 0, 0));
        assertThat(notice.endAt()).isEqualTo(LocalDateTime.of(2021, 5, 27, 23, 59));
        assertThat(notice.description()).contains("정원: 29");
    }

    @Test
    void 평생교육과_예절교육_공고를_강의_시간과_함께_바꾼다() {
        NoticeData lifelong = new YonginLifelongEducationCollector(null, "key").toNotice(new HashMap<>(Map.of(
                "평생교육명", "[열린공방] 우드DIY '선반 만들기'", "평생교육시작일", "2014-07-15", "평생교육종료일", "2014-07-15",
                "강의시작시간", "10:00", "강의종료시간", "12:00", "평생교육장소", "용인시여성회관 4층 열린공간")));
        NoticeData etiquette = new YonginEtiquetteEducationCollector(null, "key").toNotice(new HashMap<>(Map.of(
                "프로그램일련번호", "242", "프로그램강좌명", "방학특강 초등예절교실 1기", "교육기간시작일", "2017-01-09",
                "교육기간종료일", "2017-01-11", "교육장소", "용인시예절교육관", "수강내용", "None", "정원수", "30")));

        assertThat(lifelong.startAt()).isEqualTo(LocalDateTime.of(2014, 7, 15, 10, 0));
        assertThat(lifelong.endAt()).isEqualTo(LocalDateTime.of(2014, 7, 15, 12, 0));
        assertThat(lifelong.placeName()).isEqualTo("용인시여성회관 4층 열린공간");
        assertThat(etiquette.externalId()).isEqualTo("YONGIN_ETIQUETTE_EDUCATION-242");
        assertThat(etiquette.endAt()).isEqualTo(LocalDateTime.of(2017, 1, 11, 23, 59));
        assertThat(etiquette.description()).contains("정원: 30").doesNotContain("None");
    }

    @Test
    void 장애인복지관_프로그램은_일정_없이_상시_프로그램으로_바꾼다() {
        Map<String, Object> row = new HashMap<>();
        row.put("CMWELFCT_NM_INFO", "용인시처인장애인종합복지관");
        row.put("PROG_TITLE", "튼튼교실(동생반)");
        row.put("PROG_DIV_NM", "아동 청소년 프로그램");
        row.put("DETAIL_DIV_NM", "None");
        row.put("USE_TARGET", "장애인");
        row.put("USE_TARGET_OBSTCL_TYPE_COND", "지적장애인+자폐성장애인");
        row.put("USE_TM_INFO", "주 1회, 16:30~17:30");
        row.put("USE_AMT", "30000");
        row.put("REFINE_ROADNM_ADDR", "경기도 용인시 처인구 중부대로1199번길 21");
        row.put("TELNO", "031-123-4567");
        row.put("DATA_STD_DE", "20190703");

        NoticeData notice = YonginWelfareCenterProgramCollector.toNotice(row);

        assertThat(notice.title()).isEqualTo("튼튼교실(동생반)");
        assertThat(notice.startAt()).isNull();
        assertThat(notice.endAt()).isNull();
        assertThat(notice.placeName()).isEqualTo("용인시처인장애인종합복지관");
        assertThat(notice.address()).isEqualTo("경기도 용인시 처인구 중부대로1199번길 21");
        assertThat(notice.description()).contains("장애유형: 지적장애인+자폐성장애인", "이용금액: 30,000원", "2019-07-03")
                .doesNotContain("None");
        assertThat(notice.externalId()).isEqualTo(YonginWelfareCenterProgramCollector.toNotice(row).externalId());
        assertThat(YonginWelfareCenterProgramCollector.category("의료재활", "물리치료")).isEqualTo(ProgramCategory.HEALTH);
        assertThat(YonginWelfareCenterProgramCollector.category("스포츠", "보치아")).isEqualTo(ProgramCategory.SPORTS);
        assertThat(YonginWelfareCenterProgramCollector.rows(Map.of())).isEqualTo(List.of());
    }
}
