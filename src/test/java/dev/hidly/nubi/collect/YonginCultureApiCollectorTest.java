package dev.hidly.nubi.collect.publicdata;

import dev.hidly.nubi.collect.NoticeData;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YonginCultureApiCollectorTest {
    private Map<String, Object> row() {
        Map<String, Object> row = new HashMap<>();
        row.put("행사일련번호", 457);
        row.put("행사제목", "그날의 이야기");
        row.put("행사시작일", "2025-09-09");
        row.put("행사종료일", "2026-01-11");
        row.put("행사장소", "벗이미술관");
        row.put("행사장소주소", "경기도 용인시 처인구 양지면 학촌로53번길 4");
        row.put("행사문의처", "031-333-2114");
        row.put("행사시간", "10:00~18:00");
        return row;
    }

    @Test
    void 공공데이터_행을_공고로_바꾼다() {
        NoticeData notice = YonginCultureApiCollector.toNotice(row());

        assertThat(notice.externalId()).isEqualTo("YONGIN_CULTURE_API-457");
        assertThat(notice.title()).isEqualTo("그날의 이야기");
        assertThat(notice.startAt()).isEqualTo(LocalDateTime.of(2025, 9, 9, 0, 0));
        assertThat(notice.endAt()).isEqualTo(LocalDateTime.of(2026, 1, 11, 23, 59));
        assertThat(notice.placeName()).isEqualTo("벗이미술관");
        assertThat(notice.address()).isEqualTo("경기도 용인시 처인구 양지면 학촌로53번길 4");
        assertThat(notice.contactPhone()).isEqualTo("031-333-2114");
        assertThat(notice.description()).isEqualTo("행사시간: 10:00~18:00");
    }

    @Test
    void 종료일이_없으면_시작일로_두고_번호나_시작일이_없으면_건너뛴다() {
        Map<String, Object> noEnd = row();
        noEnd.put("행사종료일", null);
        assertThat(YonginCultureApiCollector.toNotice(noEnd).endAt()).isEqualTo(LocalDateTime.of(2025, 9, 9, 23, 59));

        Map<String, Object> noId = row();
        noId.remove("행사일련번호");
        assertThat(YonginCultureApiCollector.toNotice(noId)).isNull();

        Map<String, Object> noStart = row();
        noStart.put("행사시작일", "");
        assertThat(YonginCultureApiCollector.toNotice(noStart)).isNull();
    }

    @Test
    void 문의처가_20자를_넘으면_자른다() {
        Map<String, Object> longPhone = row();
        longPhone.put("행사문의처", "031-333-2114 (문화예술과 담당자)");
        assertThat(YonginCultureApiCollector.toNotice(longPhone).contactPhone()).hasSize(20);
    }
}
