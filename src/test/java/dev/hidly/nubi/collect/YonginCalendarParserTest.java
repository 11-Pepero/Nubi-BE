package dev.hidly.nubi.collect;

import dev.hidly.nubi.collect.calendar.YonginCalendarParser;
import dev.hidly.nubi.collect.calendar.YonginCalendarParser.Detail;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class YonginCalendarParserTest {
    @Test
    void 달력_목록에서_행사_번호를_중복_없이_뽑는다() {
        String html = """
                <div class="category_popup"><div>
                  <div>어울림 사진전</div><div>2026-11-01~2026-11-03</div><div>문화예술원</div>
                  <a href="BD_selectCultureArt.do?q_showIdx=101&q_year=2026&q_month=11" class="button2 detailView">자세히 보기</a>
                </div></div>
                <a href="BD_selectCultureArt.do?q_showIdx=101&q_year=2026&q_month=11">자세히 보기</a>
                <a href="BD_selectCultureArt.do?q_showIdx=202&q_year=2026&q_month=11">자세히 보기</a>
                <a href="/other.do">다른 링크</a>
                """;

        assertThat(YonginCalendarParser.parseShowIds(html)).containsExactly("101", "202");
    }

    @Test
    void 상세에서_제목_일시_장소와_정보_항목을_읽는다() {
        String html = """
                <h3 class="detail_title">어울림 사진전 </h3>
                <ul class="info_list">
                  <li class="info_item"><em class="title">일시</em><em class="desc">2026-10-28 ~ 2026-11-01</em></li>
                  <li class="info_item"><em class="title">장소</em><em class="desc">용인포은아트갤러리</em></li>
                  <li class="info_item"><em class="title">관람시간</em><em class="desc">수~토 10:00-18:00
                일 10:00-16:00</em></li>
                  <li class="info_item"><em class="title">가격</em><em class="desc">무료</em></li>
                  <!-- <li class="info_item"><em class="title">가격</em><em class="desc">유료</em></li> -->
                  <li class="info_item"><em class="title">기타정보</em><em class="desc"></em></li>
                </ul>
                <div class="view_text"><p>11/1(일)은 16시까지 운영합니다.</p><img src="poster.jpg"/></div>
                """;

        Detail detail = YonginCalendarParser.parseDetail(html);

        assertThat(detail.title()).isEqualTo("어울림 사진전");
        assertThat(detail.startDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(detail.endDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(detail.info()).containsEntry("장소", "용인포은아트갤러리")
                .containsEntry("관람시간", "수~토 10:00-18:00 / 일 10:00-16:00")
                .containsEntry("가격", "무료");
        assertThat(detail.content()).isEqualTo("11/1(일)은 16시까지 운영합니다.");
    }

    @Test
    void 하루짜리_행사는_종료일이_시작일과_같고_제목이_없으면_null() {
        Detail oneDay = YonginCalendarParser.parseDetail("""
                <h3 class="detail_title">정기공연</h3>
                <li class="info_item"><em class="title">일시</em><em class="desc">2026-11-04</em></li>
                """);
        assertThat(oneDay.endDate()).isEqualTo(oneDay.startDate());

        assertThat(YonginCalendarParser.parseDetail("<div>내용 없음</div>")).isNull();
    }
}
