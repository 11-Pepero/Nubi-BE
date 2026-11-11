package dev.hidly.nubi.collect.facility;

import dev.hidly.nubi.collect.facility.BarrierFreeTourClient.Place;
import dev.hidly.nubi.program.data.enums.FacilityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BarrierFreeMatcherTest {
    private static final Place PO_EUN = new Place("1753401", "용인포은아트홀", "14",
            "경기도 용인시 수지구 포은대로 499 (죽전동)", 37.3236, 127.1095);
    private static final Place BUT_I = new Place("2", "벗이미술관", "14",
            "경기도 용인시 처인구 양지면 학촌로53번길 4", 37.2431, 127.2832);
    private static final Place HOAM = new Place("3", "호암미술관", "14",
            "경기도 용인시 처인구 포곡읍 에버랜드로562번길 38", 37.2944, 127.1972);
    private static final List<Place> PLACES = List.of(PO_EUN, BUT_I, HOAM);

    @Test
    void 도로명주소가_같고_이름이_겹치면_잇는다() {
        assertThat(BarrierFreeMatcher.match("용인포은아트홀", "경기 용인시 수지구 포은대로 499", null, null, PLACES))
                .contains(PO_EUN);
    }

    @Test
    void 갤러리_홀_같은_말을_빼고_이름이_같으면_같은_건물로_본다() {
        assertThat(BarrierFreeMatcher.match("용인포은아트갤러리", "경기 용인시 수지구 포은대로 499", null, null, PLACES))
                .contains(PO_EUN);
    }

    @Test
    void 주소만_같고_이름이_다르면_잇지_않는다() {
        Place maru = new Place("4", "문화예술원 마루홀", "14", "경기도 용인시 처인구 중부대로 1199 (삼가동)", 37.2343, 127.2010);
        assertThat(BarrierFreeMatcher.match("처인구보건소", "경기 용인시 처인구 중부대로 1199", 37.2343, 127.2010, List.of(maru)))
                .isEmpty();
        assertThat(BarrierFreeMatcher.match("마루홀", "경기 용인시 처인구 중부대로 1199", 37.2343, 127.2010, List.of(maru)))
                .contains(maru);
    }

    @Test
    void 이름이_겹치면_주소가_달라도_300m_안이면_잇는다() {
        assertThat(BarrierFreeMatcher.match("호암미술관", "경기도 용인시 처인구 포곡읍 가실리 204", 37.2950, 127.1980, PLACES))
                .contains(HOAM);
        // 멀리 떨어져 있으면 잇지 않는다
        assertThat(BarrierFreeMatcher.match("호암미술관", "경기도 용인시 처인구 포곡읍 가실리 204", 37.30, 127.25, PLACES))
                .isEmpty();
    }

    @Test
    void 미술관_박물관_같은_흔한_말만_겹치면_잇지_않는다() {
        assertThat(BarrierFreeMatcher.namesOverlap("한국미술관", "벗이미술관")).isFalse();
        assertThat(BarrierFreeMatcher.namesOverlap("용인시박물관", "명지대학교 박물관")).isFalse();
        assertThat(BarrierFreeMatcher.namesOverlap("단국대석주선기념박물관", "단국대학교 석주선기념박물관")).isTrue();
        assertThat(BarrierFreeMatcher.match("한국미술관", "경기도 용인시 기흥구 마북로 244-2", 37.2431, 127.2832, PLACES))
                .isEmpty();
    }

    @Test
    void 무장애_상세에_있다고_적힌_것만_편의시설로_바꾼다() {
        assertThat(BarrierFreeMatcher.toFacilityTypes(Map.of(
                "exit", "주 출입구는 턱이 없어 휠체어 접근 가능함",
                "elevator", "엘리베이터 있음",
                "restroom", "장애인 화장실 있음",
                "parking", "장애인 주차장 있음(10대,B1~B2)",
                "route", "출입구까지 경사로가 설치되어 있음(완만함)",
                "auditorium", "장애인 관람석 있음(1층 10석, 2층 6석)")))
                .containsExactlyInAnyOrder(FacilityType.NO_STEP, FacilityType.ELEVATOR, FacilityType.ACCESSIBLE_TOILET,
                        FacilityType.PARKING, FacilityType.RAMP);

        // 일반 화장실·일반 주차장, "없음"은 넣지 않는다
        assertThat(BarrierFreeMatcher.toFacilityTypes(Map.of(
                "restroom", "화장실 있음",
                "parking", "주차장 있음",
                "elevator", "엘리베이터 없음",
                "route", "출입구까지 단차가 있음")))
                .isEmpty();
    }

    @Test
    void 응답의_items가_하나여도_여럿이어도_비어도_읽는다() {
        String one = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
                  {"contentid":"1753401","elevator":"엘리베이터 있음","braileblock":"","restroom":"장애인 화장실 있음"}]},
                  "numOfRows":1,"pageNo":1,"totalCount":1}}}
                """;
        assertThat(BarrierFreeTourClient.detailOf(BarrierFreeTourClient.bodyOf(one)))
                .containsExactly(Map.entry("elevator", "엘리베이터 있음"), Map.entry("restroom", "장애인 화장실 있음"));

        String empty = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}
                """;
        assertThat(BarrierFreeTourClient.detailOf(BarrierFreeTourClient.bodyOf(empty))).isEmpty();

        // 인증키 오류(XML)나 오류 코드는 실패(null)로 본다
        assertThat(BarrierFreeTourClient.bodyOf("<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>")).isNull();
        assertThat(BarrierFreeTourClient.bodyOf("""
                {"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}
                """)).isNull();
    }
}
