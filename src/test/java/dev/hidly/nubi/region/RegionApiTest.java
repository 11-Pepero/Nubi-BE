package dev.hidly.nubi.region;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.collect.kakao.KakaoLocalClient;
import dev.hidly.nubi.common.geo.GeoPoints;
import dev.hidly.nubi.region.service.VenueEmdFiller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.entity.VenueFacility;
import dev.hidly.nubi.program.data.enums.FacilitySource;
import dev.hidly.nubi.program.data.enums.FacilityStatus;
import dev.hidly.nubi.program.data.enums.FacilityType;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.program.data.repository.VenueFacilityRepository;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RegionApiTest {
    private static final String MONTH = "2099-06";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private VenueFacilityRepository venueFacilityRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private VenueEmdFiller venueEmdFiller;
    @MockitoBean
    private KakaoLocalClient kakaoLocalClient;

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

    private Venue venue(District district, boolean accessible) {
        Venue venue = venueRepository.save(Venue.builder().name("지도 테스트 장소 " + UUID.randomUUID()).district(district).build());
        if (accessible) {
            venueFacilityRepository.save(VenueFacility.builder().venue(venue).facilityType(FacilityType.ELEVATOR)
                    .status(FacilityStatus.YES).source(FacilitySource.PUBLIC_DATA).build());
        }
        return venue;
    }

    private void program(Venue venue, ProgramStatus status, LocalDateTime startAt) {
        programRepository.save(Program.builder().title("지도 테스트 프로그램").venue(venue).status(status).startAt(startAt).build());
    }

    private String districts(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/regions/districts").param("month", MONTH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static long count(String response, String district, String field) {
        List<Number> values = JsonPath.read(response, "$.data.districts[?(@.district=='" + district + "')]." + field);
        return values.get(0).longValue();
    }

    @Test
    void 구별로_시설_장애인_수와_그_달_프로그램_수를_센다() throws Exception {
        String token = token();
        String before = districts(token);

        Venue plain = venue(District.CHEOIN, false);
        Venue accessible = venue(District.CHEOIN, true);
        LocalDateTime inMonth = LocalDateTime.of(2099, 6, 10, 10, 0);
        program(plain, ProgramStatus.OPEN, inMonth);
        program(accessible, ProgramStatus.CLOSED, inMonth);
        program(plain, ProgramStatus.DRAFT, inMonth);
        program(plain, ProgramStatus.OPEN, LocalDateTime.of(2099, 7, 1, 10, 0));
        program(venue(District.SUJI, true), ProgramStatus.OPEN, inMonth);

        String after = districts(token);
        assertThat(count(after, "CHEOIN", "programCount") - count(before, "CHEOIN", "programCount")).isEqualTo(2);
        assertThat(count(after, "CHEOIN", "accessibleProgramCount") - count(before, "CHEOIN", "accessibleProgramCount")).isEqualTo(1);
        assertThat(count(after, "SUJI", "programCount") - count(before, "SUJI", "programCount")).isEqualTo(1);
        assertThat(count(after, "GIHEUNG", "programCount")).isEqualTo(count(before, "GIHEUNG", "programCount"));

        mockMvc.perform(get("/api/v1/regions/districts").param("month", MONTH).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.month").value(MONTH))
                .andExpect(jsonPath("$.data.districts.length()").value(3))
                .andExpect(jsonPath("$.data.districts[0].district").value("CHEOIN"))
                .andExpect(jsonPath("$.data.districts[0].name").value("처인구"))
                .andExpect(jsonPath("$.data.districts[0].facilityCount").value(23))
                .andExpect(jsonPath("$.data.districts[0].facilitiesPerThousand").value(1.6))
                .andExpect(jsonPath("$.data.districts[2].facilitiesPerThousand").value(0.3));
    }

    private static final String BAEKAM = "4146135000";

    @Test
    void 읍면동_상세는_구_요약과_읍면동별_수를_참여_기회가_적은_순으로_준다() throws Exception {
        String token = token();
        String before = mockMvc.perform(get("/api/v1/regions/districts/CHEOIN").param("month", MONTH).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        Venue baekam = venueRepository.save(Venue.builder().name("백암 테스트 장소 " + UUID.randomUUID())
                .district(District.CHEOIN).emdCode(BAEKAM).build());
        program(baekam, ProgramStatus.OPEN, LocalDateTime.of(2099, 6, 3, 10, 0));

        String after = mockMvc.perform(get("/api/v1/regions/districts/CHEOIN").param("month", MONTH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.name").value("처인구"))
                .andExpect(jsonPath("$.data.summary.registeredDisabled").value(13964))
                .andExpect(jsonPath("$.data.emds.length()").value(13))
                .andReturn().getResponse().getContentAsString();

        List<Number> beforeCount = JsonPath.read(before, "$.data.emds[?(@.emdCode=='" + BAEKAM + "')].programCount");
        List<Number> afterCount = JsonPath.read(after, "$.data.emds[?(@.emdCode=='" + BAEKAM + "')].programCount");
        assertThat(afterCount.get(0).longValue() - beforeCount.get(0).longValue()).isEqualTo(1);
        List<String> names = JsonPath.read(after, "$.data.emds[*].emdName");
        assertThat(names).contains("백암면", "양지읍", "이동읍").doesNotContain("이동면", "양지면");
        List<Number> programs = JsonPath.read(after, "$.data.emds[*].programCount");
        assertThat(programs.stream().map(Number::longValue).toList()).isSorted();
        List<Number> facilities = JsonPath.read(after, "$.data.emds[*].facilityCount");
        assertThat(facilities.stream().mapToInt(Number::intValue).sum()).isEqualTo(23);
    }

    @Test
    void 좌표만_있는_장소에_행정동_코드를_채운다() {
        Venue venue = venueRepository.save(Venue.builder().name("행정동 테스트 장소 " + UUID.randomUUID())
                .location(GeoPoints.of(37.17, 127.37)).build());
        given(kakaoLocalClient.isEnabled()).willReturn(true);
        given(kakaoLocalClient.findAdministrativeDongCode(anyDouble(), anyDouble())).willReturn(Optional.of(BAEKAM));

        venueEmdFiller.fill();

        assertThat(venueRepository.findById(venue.getId()).orElseThrow().getEmdCode()).isEqualTo(BAEKAM);
    }

    @Test
    void 없는_구면_400() throws Exception {
        mockMvc.perform(get("/api/v1/regions/districts/SEOUL").header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않으면_401_잘못된_달이면_400() throws Exception {
        mockMvc.perform(get("/api/v1/regions/districts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/regions/districts").param("month", "2099-13").header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }
}
