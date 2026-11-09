package dev.hidly.nubi.program;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.common.geo.GeoPoints;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VenueSearchApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private VenueRepository venueRepository;

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

    @Test
    void 이름이나_주소에_키워드가_들어간_장소를_이름순으로_찾는다() throws Exception {
        String word = "검색" + UUID.randomUUID().toString().substring(0, 8);
        venueRepository.save(Venue.builder().name("나 " + word + " 아트홀").address("용인시 처인구 중부대로 1199")
                .district(District.CHEOIN).location(GeoPoints.of(37.23, 127.2)).build());
        venueRepository.save(Venue.builder().name("가 체육관").address("용인시 수지구 " + word.toUpperCase() + "로 1")
                .district(District.SUJI).build());
        venueRepository.save(Venue.builder().name("상관없는 장소").build());

        mockMvc.perform(get("/api/v1/venues").param("keyword", " " + word + " ").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].name").value("가 체육관"))
                .andExpect(jsonPath("$.data.content[0].latitude").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].name").value("나 " + word + " 아트홀"))
                .andExpect(jsonPath("$.data.content[1].district").value("CHEOIN"))
                .andExpect(jsonPath("$.data.content[1].latitude").value(37.23));
    }

    @Test
    void 퍼센트만_넣으면_전체가_아니라_글자로_찾는다() throws Exception {
        mockMvc.perform(get("/api/v1/venues").param("keyword", "%" + UUID.randomUUID()).header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 로그인하지_않으면_401_잘못된_페이지면_400() throws Exception {
        mockMvc.perform(get("/api/v1/venues"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/venues").param("size", "101").header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }
}
