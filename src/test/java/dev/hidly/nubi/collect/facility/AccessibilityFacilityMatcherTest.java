package dev.hidly.nubi.collect.facility;

import dev.hidly.nubi.collect.facility.AccessibilityFacilityClient.Place;
import dev.hidly.nubi.program.data.enums.FacilityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccessibilityFacilityMatcherTest {

    private static Place place(String name, String type, String address) {
        return new Place(name + "-id", name, type, address, null, null);
    }

    @Test
    void 도로명이나_지번에서_주소_열쇠를_만든다() {
        assertThat(AccessibilityFacilityMatcher.addressKey("경기도 용인시 수지구 포은대로 499")).contains("포은대로499");
        assertThat(AccessibilityFacilityMatcher.addressKey("경기 용인시 처인구 양지면 학촌로53번길 4")).contains("학촌로53번길4");
        assertThat(AccessibilityFacilityMatcher.addressKey("경기도 용인시 처인구 남동 555 명지대학교")).contains("남동555");
        assertThat(AccessibilityFacilityMatcher.addressKey("용인시 어딘가")).isEmpty();
        assertThat(AccessibilityFacilityMatcher.addressKey(null)).isEmpty();
    }

    @Test
    void 같은_주소의_시설이_하나면_잇고_여럿이면_이름이_겹치는_것만_잇는다() {
        Map<String, List<Place>> index = AccessibilityFacilityMatcher.indexByAddress(List.of(
                place("용인문화유적전시관", "전시장", "경기도 용인시 기흥구 동백3로 79"),
                place("용인시청 지하2층", "공공청사", "경기도 용인시 처인구 중부대로 1199"),
                place("용인시청상록어린이집", "어린이집", "경기도 용인시 처인구 중부대로 1199"),
                place("럭스나인", "아파트", "경기도 용인시 처인구 명지로 24"),
                place("용인역북 THE 럭스나인", "금융업소 등 일반업무시설", "경기도 용인시 처인구 명지로 24"),
                place("용인시처인장애인복지관", "금융업소 등 일반업무시설", "경기도 용인시 처인구 경안천로 318")));

        assertThat(AccessibilityFacilityMatcher.match("용인시박물관", "경기도 용인시 기흥구 동백3로 79", index))
                .map(Place::name).contains("용인문화유적전시관");
        assertThat(AccessibilityFacilityMatcher.match("용인시청 에이스홀", "경기 용인시 처인구 중부대로 1199", index)).isEmpty();
        assertThat(AccessibilityFacilityMatcher.match("처인홀", "경기 용인시 처인구 명지로 24", index)).isEmpty();
        assertThat(AccessibilityFacilityMatcher.match("용인시처인장애인복지관", "경기도 용인시 처인구 경안천로 318", index))
                .map(Place::name).contains("용인시처인장애인복지관");
    }

    @Test
    void 설치된_편의시설_이름을_편의시설_종류로_바꾼다() {
        assertThat(AccessibilityFacilityMatcher.toFacilityTypes(List.of(
                "승강기", "유도 및 안내 설비", "장애인사용가능화장실", "장애인전용주차구역", "주출입구 높이차이 제거", "주출입구 접근로")))
                .containsExactlyInAnyOrder(FacilityType.ELEVATOR, FacilityType.BRAILLE_BLOCK, FacilityType.ACCESSIBLE_TOILET,
                        FacilityType.PARKING, FacilityType.NO_STEP);
        assertThat(AccessibilityFacilityMatcher.toFacilityTypes(List.of("경사로"))).containsExactly(FacilityType.RAMP);
    }
}
