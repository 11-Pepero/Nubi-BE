package dev.hidly.nubi.collect.kakao;

import dev.hidly.nubi.collect.kakao.KakaoLocalClient.PlaceCandidate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoLocalClientTest {

    @Test
    void 카카오_응답의_분류는_마지막_단계만_쓰고_빈_값은_비운다() {
        PlaceCandidate place = KakaoLocalClient.toCandidate(Map.of(
                "id", "8", "place_name", "용인포은아트홀", "category_name", "문화,예술 > 공연장",
                "road_address_name", "", "address_name", "경기 용인시 수지구 풍덕천동 1", "phone", "",
                "x", "127.0957", "y", "37.3221"));

        assertThat(place.category()).isEqualTo("공연장");
        assertThat(place.roadAddress()).isNull();
        assertThat(place.phone()).isNull();
        assertThat(place.displayAddress()).isEqualTo("경기 용인시 수지구 풍덕천동 1");
        assertThat(place.latitude()).isEqualTo(37.3221);
        assertThat(place.longitude()).isEqualTo(127.0957);
    }

    @Test
    void 주차장과_매표소는_부속_장소로_본다() {
        assertThat(KakaoLocalClient.isAuxiliary(new PlaceCandidate("1", "용인포은아트홀 주차장", "주차장", null, "주소", null, 37, 127))).isTrue();
        assertThat(KakaoLocalClient.isAuxiliary(new PlaceCandidate("2", "에버랜드 매표소", "매표소", null, "주소", null, 37, 127))).isTrue();
        assertThat(KakaoLocalClient.isAuxiliary(new PlaceCandidate("3", "용인포은아트홀", "공연장,연극극장", null, "주소", null, 37, 127))).isFalse();
    }
}
