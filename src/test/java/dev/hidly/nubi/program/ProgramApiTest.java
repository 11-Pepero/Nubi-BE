package dev.hidly.nubi.program;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.common.geo.GeoPoints;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramImage;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.entity.VenueFacility;
import dev.hidly.nubi.program.data.enums.*;
import dev.hidly.nubi.program.data.repository.ProgramImageRepository;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProgramApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramImageRepository programImageRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private VenueFacilityRepository venueFacilityRepository;

    private record Login(String accessToken, Long organizationId) {
    }

    private Login signUp() throws Exception {
        String body = """
                {"orgName":"용인 주간활동센터","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                """.formatted("test-" + UUID.randomUUID() + "@nubi.dev");
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number organizationId = JsonPath.read(response, "$.data.organizationId");
        return new Login(JsonPath.read(response, "$.data.accessToken"), organizationId.longValue());
    }

    private String uniqueWord() {
        return "검색" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Venue createVenue(String name, District district) {
        return venueRepository.save(Venue.builder()
                .name(name)
                .address("용인시 수지구 포은대로 435")
                .district(district)
                .location(GeoPoints.of(37.3222, 127.0978))
                .build());
    }

    private void addFacility(Venue venue, FacilityType type, FacilityStatus status, FacilitySource source, LocalDateTime verifiedAt) {
        venueFacilityRepository.save(VenueFacility.builder()
                .venue(venue)
                .facilityType(type)
                .status(status)
                .source(source)
                .verifiedAt(verifiedAt)
                .build());
    }

    private Program createProgram(String title, Venue venue, ProgramStatus status, LocalDateTime startAt) {
        return programRepository.save(Program.builder()
                .title(title)
                .hostName("용인시 장애인복지관")
                .venue(venue)
                .category(ProgramCategory.CULTURE)
                .applyMethod(ApplyMethod.PHONE)
                .startAt(startAt)
                .endAt(startAt == null ? null : startAt.plusHours(2))
                .status(status)
                .build());
    }

    @Test
    void 로그인_없이_공개_중인_프로그램을_검색하고_대표_사진을_받는다() throws Exception {
        String word = uniqueWord();
        Venue venue = createVenue("수지 체육관", District.SUJI);
        Program program = createProgram(word + " 어울림 체육대회", venue, ProgramStatus.OPEN, LocalDateTime.of(2026, 12, 5, 10, 0));
        programImageRepository.save(ProgramImage.builder().program(program).url("/uploads/second.png").sortOrder(1).build());
        programImageRepository.save(ProgramImage.builder().program(program).url("/uploads/first.png").sortOrder(0).build());

        mockMvc.perform(get("/api/v1/programs").param("keyword", word))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content[0].programId").value(program.getId()))
                .andExpect(jsonPath("$.data.content[0].hostName").value("용인시 장애인복지관"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value("/uploads/first.png"))
                .andExpect(jsonPath("$.data.content[0].venue.name").value("수지 체육관"))
                .andExpect(jsonPath("$.data.content[0].venue.district").value("SUJI"));
    }

    @Test
    void 임시_저장_끝난_행사_삭제된_행사는_검색되지_않는다() throws Exception {
        String word = uniqueWord();
        createProgram(word + " 공개", null, ProgramStatus.OPEN, null);
        createProgram(word + " 마감", null, ProgramStatus.CLOSED, null);
        createProgram(word + " 임시", null, ProgramStatus.DRAFT, null);
        createProgram(word + " 끝남", null, ProgramStatus.ENDED, null);

        mockMvc.perform(get("/api/v1/programs").param("keyword", word))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void 구_월_신청방법_분류로_거른다() throws Exception {
        String word = uniqueWord();
        Venue suji = createVenue("수지 문화센터", District.SUJI);
        Venue cheoin = createVenue("처인 문화센터", District.CHEOIN);
        createProgram(word + " 수지 12월", suji, ProgramStatus.OPEN, LocalDateTime.of(2026, 12, 10, 10, 0));
        createProgram(word + " 처인 12월", cheoin, ProgramStatus.OPEN, LocalDateTime.of(2026, 12, 20, 10, 0));
        createProgram(word + " 수지 1월", suji, ProgramStatus.OPEN, LocalDateTime.of(2027, 1, 3, 10, 0));
        programRepository.save(Program.builder().title(word + " 수지 11월부터 12월까지").venue(suji)
                .startAt(LocalDateTime.of(2026, 11, 20, 10, 0)).endAt(LocalDateTime.of(2026, 12, 2, 10, 0))
                .applyMethod(ApplyMethod.ONLINE).category(ProgramCategory.SPORTS).status(ProgramStatus.OPEN).build());

        mockMvc.perform(get("/api/v1/programs").param("keyword", word).param("district", "SUJI"))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mockMvc.perform(get("/api/v1/programs").param("keyword", word).param("month", "2026-12"))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].title").value(word + " 수지 11월부터 12월까지"));

        mockMvc.perform(get("/api/v1/programs").param("keyword", word).param("district", "SUJI").param("month", "2026-12"))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/programs").param("keyword", word).param("applyMethod", "ONLINE"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/programs").param("keyword", word).param("category", "SPORTS"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 접근성_정보가_확인된_장소의_프로그램만_거른다() throws Exception {
        String word = uniqueWord();
        Venue accessible = createVenue("엘리베이터 있는 곳", District.GIHEUNG);
        addFacility(accessible, FacilityType.ELEVATOR, FacilityStatus.YES, FacilitySource.PUBLIC_DATA, null);
        Venue parkingOnly = createVenue("주차만 있는 곳", District.GIHEUNG);
        addFacility(parkingOnly, FacilityType.PARKING, FacilityStatus.YES, FacilitySource.HOST, null);
        Venue noRamp = createVenue("경사로 없는 곳", District.GIHEUNG);
        addFacility(noRamp, FacilityType.RAMP, FacilityStatus.NO, FacilitySource.REVIEW, null);
        createProgram(word + " 가능", accessible, ProgramStatus.OPEN, null);
        createProgram(word + " 주차만", parkingOnly, ProgramStatus.OPEN, null);
        createProgram(word + " 경사로 없음", noRamp, ProgramStatus.OPEN, null);
        createProgram(word + " 장소 없음", null, ProgramStatus.OPEN, null);

        mockMvc.perform(get("/api/v1/programs").param("keyword", word).param("accessibleOnly", "true"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value(word + " 가능"));
    }

    @Test
    void 페이지_크기대로_나눠_받는다() throws Exception {
        String word = uniqueWord();
        for (int i = 1; i <= 3; i++) {
            createProgram(word + " " + i, null, ProgramStatus.OPEN, LocalDateTime.of(2026, 12, i, 10, 0));
        }

        mockMvc.perform(get("/api/v1/programs").param("keyword", word).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value(word + " 3"));
    }

    @Test
    void 잘못된_검색_조건은_400() throws Exception {
        mockMvc.perform(get("/api/v1/programs").param("month", "2026-13"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/programs").param("district", "SEOUL"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/programs").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인_없이_프로그램_상세를_사진_장소_편의시설과_함께_본다() throws Exception {
        Venue venue = createVenue("수지 체육관", District.SUJI);
        addFacility(venue, FacilityType.ELEVATOR, FacilityStatus.YES, FacilitySource.PUBLIC_DATA, null);
        Program program = createProgram("어울림 체육대회", venue, ProgramStatus.OPEN, LocalDateTime.of(2026, 12, 5, 10, 0));
        programImageRepository.save(ProgramImage.builder().program(program).url("/uploads/a.png").sortOrder(0).build());

        mockMvc.perform(get("/api/v1/programs/{programId}", program.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("어울림 체육대회"))
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andExpect(jsonPath("$.data.venue.name").value("수지 체육관"))
                .andExpect(jsonPath("$.data.venue.latitude").value(37.3222))
                .andExpect(jsonPath("$.data.venue.longitude").value(127.0978))
                .andExpect(jsonPath("$.data.facilities.length()").value(6))
                .andExpect(jsonPath("$.data.facilities[0].facilityType").value("ELEVATOR"))
                .andExpect(jsonPath("$.data.facilities[0].status").value("YES"));
    }

    @Test
    void 임시_저장_행사_상세는_주최_기관만_본다() throws Exception {
        Login host = signUp();
        Login other = signUp();
        Long programId = programRepository.save(Program.builder()
                .hostOrganization(organizationRepository.findById(host.organizationId()).orElseThrow())
                .title("임시 저장 행사")
                .build()).getId();

        mockMvc.perform(get("/api/v1/programs/{programId}", programId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/programs/{programId}", programId)
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/programs/{programId}", programId)
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hostName").value("용인 주간활동센터"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void 장소_편의시설은_출처를_합치고_가장_최근에_확인된_값을_쓴다() throws Exception {
        Venue venue = createVenue("수지 체육관", District.SUJI);
        addFacility(venue, FacilityType.ACCESSIBLE_TOILET, FacilityStatus.YES, FacilitySource.PUBLIC_DATA, LocalDateTime.of(2025, 1, 1, 0, 0));
        addFacility(venue, FacilityType.ACCESSIBLE_TOILET, FacilityStatus.NO, FacilitySource.REVIEW, LocalDateTime.of(2026, 10, 1, 0, 0));
        addFacility(venue, FacilityType.RAMP, FacilityStatus.YES, FacilitySource.HOST, LocalDateTime.of(2026, 1, 1, 0, 0));
        addFacility(venue, FacilityType.RAMP, FacilityStatus.UNKNOWN, FacilitySource.REVIEW, LocalDateTime.of(2026, 10, 1, 0, 0));

        mockMvc.perform(get("/api/v1/venues/{venueId}/facilities", venue.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수지 체육관"))
                .andExpect(jsonPath("$.data.facilities.length()").value(6))
                .andExpect(jsonPath("$.data.facilities[0].facilityType").value("ELEVATOR"))
                .andExpect(jsonPath("$.data.facilities[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.facilities[0].sources.length()").value(0))
                .andExpect(jsonPath("$.data.facilities[1].facilityType").value("ACCESSIBLE_TOILET"))
                .andExpect(jsonPath("$.data.facilities[1].status").value("NO"))
                .andExpect(jsonPath("$.data.facilities[1].sources.length()").value(2))
                .andExpect(jsonPath("$.data.facilities[1].sources[0].source").value("PUBLIC_DATA"))
                .andExpect(jsonPath("$.data.facilities[2].facilityType").value("RAMP"))
                .andExpect(jsonPath("$.data.facilities[2].status").value("YES"));
    }

    @Test
    void 없는_장소면_404() throws Exception {
        mockMvc.perform(get("/api/v1/venues/{venueId}/facilities", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VENUE_NOT_FOUND"));
    }
}
