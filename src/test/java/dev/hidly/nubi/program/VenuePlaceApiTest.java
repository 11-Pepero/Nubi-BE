package dev.hidly.nubi.program;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.collect.kakao.KakaoLocalClient;
import dev.hidly.nubi.collect.kakao.KakaoLocalClient.PlaceCandidate;
import dev.hidly.nubi.collect.service.VenueFacilitySync;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VenuePlaceApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private VenueRepository venueRepository;
    // 테스트가 실제 카카오와 공공데이터를 부르지 않게 한다.
    @MockitoBean
    private KakaoLocalClient kakaoLocalClient;
    @MockitoBean
    private VenueFacilitySync venueFacilitySync;

    private String token() throws Exception {
        String body = """
                {"orgName":"용인 주간활동센터","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                """.formatted("test-" + UUID.randomUUID() + "@nubi.dev");
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    private static String unique(String name) {
        return name + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void 카카오_장소를_찾고_이미_등록된_장소는_venueId를_같이_준다() throws Exception {
        String registered = unique("포은아트홀");
        String fresh = unique("새 체육관");
        // 수집할 때는 "경기도"로, 카카오는 "경기"로 써도 같은 장소로 본다
        Venue venue = venueRepository.save(Venue.builder().name(registered).address("경기도 용인시 수지구 포은대로 499").build());
        when(kakaoLocalClient.searchPlaces(eq("아트홀"), anyInt())).thenReturn(List.of(
                new PlaceCandidate("101", registered, "공연장", "경기 용인시 수지구 포은대로 499", "경기 용인시 수지구 풍덕천동 1", "031-260-3355",
                        37.3221, 127.0957),
                new PlaceCandidate("102", fresh, "체육관", null, "경기 용인시 처인구 삼가동 5", null, 37.24, 127.2)));

        mockMvc.perform(get("/api/v1/venues/places").param("query", "아트홀").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].kakaoPlaceId").value("101"))
                .andExpect(jsonPath("$.data[0].category").value("공연장"))
                .andExpect(jsonPath("$.data[0].roadAddress").value("경기 용인시 수지구 포은대로 499"))
                .andExpect(jsonPath("$.data[0].venueId").value(venue.getId()))
                .andExpect(jsonPath("$.data[1].venueId").doesNotExist())
                .andExpect(jsonPath("$.data[1].latitude").value(37.24));
    }

    @Test
    void 고른_장소를_구_좌표_행정동과_함께_등록하고_같은_장소는_다시_만들지_않는다() throws Exception {
        String token = token();
        String name = unique("용인실내체육관");
        when(kakaoLocalClient.findAdministrativeDongCode(anyDouble(), anyDouble())).thenReturn(Optional.of("4146110100"));
        String body = """
                {"name":"%s","address":"경기 용인시 처인구 경안천로 76","latitude":37.2386,"longitude":127.2051}
                """.formatted(name);

        String first = mockMvc.perform(post("/api/v1/venues").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.district").value("CHEOIN"))
                .andExpect(jsonPath("$.data.latitude").value(37.2386))
                .andReturn().getResponse().getContentAsString();
        Number venueId = JsonPath.read(first, "$.data.venueId");

        mockMvc.perform(post("/api/v1/venues").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.venueId").value(venueId.longValue()));
        // "경기도"로 쓴 같은 주소도 같은 장소다
        mockMvc.perform(post("/api/v1/venues").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("경기 용인시", "경기도 용인시")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.venueId").value(venueId.longValue()));

        Venue saved = venueRepository.findById(venueId.longValue()).orElseThrow();
        assertThat(saved.getEmdCode()).isEqualTo("4146110100");
        assertThat(saved.getLocationCheckedAt()).isNotNull();
        // 등록이 끝나면 편의시설을 뒤에서 채운다
        verify(venueFacilitySync, org.mockito.Mockito.atLeastOnce()).syncVenueLater(venueId.longValue());
    }

    @Test
    void 이름만_있던_장소를_고르면_그_장소에_주소와_좌표를_채운다() throws Exception {
        String name = unique("요한의 집");
        Venue nameOnly = venueRepository.save(Venue.builder().name(name).build());

        mockMvc.perform(post("/api/v1/venues").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","address":"경기 용인시 기흥구 중부대로 468","latitude":37.27,"longitude":127.11}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.venueId").value(nameOnly.getId()))
                .andExpect(jsonPath("$.data.address").value("경기 용인시 기흥구 중부대로 468"))
                .andExpect(jsonPath("$.data.district").value("GIHEUNG"))
                .andExpect(jsonPath("$.data.longitude").value(127.11));
    }

    @Test
    void 검색어나_좌표가_잘못되면_400_로그인하지_않으면_401() throws Exception {
        String token = token();
        mockMvc.perform(get("/api/v1/venues/places").param("query", " ").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/venues").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"바다 위","address":"어딘가","latitude":10.0,"longitude":127.0}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/venues/places").param("query", "아트홀"))
                .andExpect(status().isUnauthorized());
        verify(venueFacilitySync, never()).syncVenueLater(org.mockito.ArgumentMatchers.anyLong());
    }
}
