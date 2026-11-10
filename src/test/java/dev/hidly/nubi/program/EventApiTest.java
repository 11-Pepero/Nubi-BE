package dev.hidly.nubi.program;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.common.geo.GeoPoints;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramCondition;
import dev.hidly.nubi.program.data.entity.ProgramImage;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.enums.ApplyMethod;
import dev.hidly.nubi.program.data.enums.ConditionSource;
import dev.hidly.nubi.program.data.enums.ConditionStatus;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.repository.ProgramConditionRepository;
import dev.hidly.nubi.program.data.repository.ProgramImageRepository;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramConditionRepository programConditionRepository;
    @Autowired
    private ProgramImageRepository programImageRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private SupportTagRepository supportTagRepository;

    private String signUp(String orgName) throws Exception {
        String body = """
                {"orgName":"%s","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                """.formatted(orgName, "event-" + UUID.randomUUID() + "@nubi.dev");
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    private long tagId(String code) {
        return supportTagRepository.findAll().stream()
                .filter(tag -> tag.getCode().equals(code))
                .map(SupportTag::getId)
                .findFirst().orElseThrow();
    }

    private Venue createVenue() {
        return venueRepository.save(Venue.builder()
                .name("센터 강당")
                .address("용인시 처인구 중부대로 1199")
                .district(District.CHEOIN)
                .location(GeoPoints.of(37.2343, 127.2010))
                .build());
    }

    // 오늘 기준 며칠 뒤 그 시각. 날짜를 박아 두면 그날이 지나 "지난 날짜로 공개 불가"에 걸려 테스트가 깨진다.
    private static String at(int daysFromNow, int hour) {
        return LocalDate.now().plusDays(daysFromNow).atTime(hour, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String eventJson(String title, String status, String startAt, String extra) {
        return """
                {"title":"%s","status":"%s","startAt":"%s"%s}
                """.formatted(title, status, startAt, extra == null ? "" : "," + extra);
    }

    private long createEvent(String token, String title, String status, String startAt) throws Exception {
        String response = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(title, status, startAt, null)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.programId")).longValue();
    }

    @Test
    void 공개하기로_행사를_등록하면_내_기관이_주최하고_바로_신청으로_저장된다() throws Exception {
        String token = signUp("용인 주간보호센터");
        Venue venue = createVenue();
        long wheelchairId = tagId("WHEELCHAIR");
        long easyWordsId = tagId("EASY_WORDS");
        String extra = """
                "category":"CULTURE","endAt":"%s","capacity":40,"venueId":%d,
                "conditions":[{"supportTagId":%d},{"supportTagId":%d,"status":"UNKNOWN","evidence":"쉬운 말 자료 준비 중"}]
                """.formatted(at(21, 16), venue.getId(), wheelchairId, easyWordsId);

        String response = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("개관 30주년 가을 축제", "OPEN", at(21, 13), extra)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("개관 30주년 가을 축제"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.applyMethod").value("DIRECT"))
                .andExpect(jsonPath("$.data.hostName").value("용인 주간보호센터"))
                .andExpect(jsonPath("$.data.capacity").value(40))
                .andExpect(jsonPath("$.data.venue.name").value("센터 강당"))
                .andReturn().getResponse().getContentAsString();

        long eventId = ((Number) JsonPath.read(response, "$.data.programId")).longValue();
        List<ProgramCondition> conditions = programConditionRepository.findAllByProgramIdOrderByIdAsc(eventId);
        assertThat(conditions).hasSize(2);
        assertThat(conditions).allMatch(condition -> condition.getSource() == ConditionSource.HOST);
        assertThat(conditions.get(0).getStatus()).isEqualTo(ConditionStatus.PROVIDED);
        assertThat(conditions.get(1).getStatus()).isEqualTo(ConditionStatus.UNKNOWN);
        assertThat(conditions.get(1).getEvidence()).isEqualTo("쉬운 말 자료 준비 중");
    }

    @Test
    void 임시_저장으로_등록할_수_있다() throws Exception {
        String token = signUp("용인 주간보호센터");

        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("겨울맞이 손뜨개 모임", "DRAFT", at(33, 10), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void 잘못된_행사_요청은_막는다() throws Exception {
        String token = signUp("용인 주간보호센터");
        long wheelchairId = tagId("WHEELCHAIR");

        // 공개 중, 임시 저장 말고는 처음부터 만들 수 없다
        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("행사", "CLOSED", at(21, 13), null)))
                .andExpect(status().isBadRequest());
        // 끝나는 시각이 시작보다 빠르다
        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("행사", "OPEN", at(21, 13), "\"endAt\":\"" + at(21, 12) + "\"")))
                .andExpect(status().isBadRequest());
        // 같은 조건을 두 번 보냈다
        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("행사", "OPEN", at(21, 13),
                                "\"conditions\":[{\"supportTagId\":%d},{\"supportTagId\":%d}]".formatted(wheelchairId, wheelchairId))))
                .andExpect(status().isBadRequest());
        // 제목이 비었다
        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(" ", "OPEN", at(21, 13), null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_장소나_지원_항목이면_404() throws Exception {
        String token = signUp("용인 주간보호센터");

        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("행사", "OPEN", at(21, 13), "\"venueId\":999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VENUE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("행사", "OPEN", at(21, 13), "\"conditions\":[{\"supportTagId\":999999999}]")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUPPORT_TAG_NOT_FOUND"));
    }

    @Test
    void 로그인하지_않으면_등록과_목록_모두_401() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("행사", "OPEN", at(21, 13), null)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 내_행사_목록은_내_기관_행사만_시작일_순으로_대표_사진과_함께_보여준다() throws Exception {
        String token = signUp("용인 주간보호센터");
        String otherToken = signUp("다른 복지관");
        long later = createEvent(token, "겨울맞이 손뜨개 모임", "OPEN", at(33, 10));
        long sooner = createEvent(token, "어울림 합창 연습", "OPEN", at(14, 15));
        createEvent(otherToken, "다른 기관 행사", "OPEN", at(10, 10));
        Program soonerEvent = programRepository.findById(sooner).orElseThrow();
        programImageRepository.save(ProgramImage.builder().program(soonerEvent).url("/uploads/cover.png").sortOrder(0).build());

        mockMvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].eventId").value(sooner))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value("/uploads/cover.png"))
                .andExpect(jsonPath("$.data.content[0].applicationOrgCount").value(0))
                .andExpect(jsonPath("$.data.content[0].participantCount").value(0))
                .andExpect(jsonPath("$.data.content[0].newApplicationCount").value(0))
                .andExpect(jsonPath("$.data.content[1].eventId").value(later));
    }

    @Test
    void 상태로_거르고_삭제된_행사는_빼고_보여준다() throws Exception {
        String token = signUp("용인 주간보호센터");
        createEvent(token, "공개 행사", "OPEN", at(14, 15));
        createEvent(token, "임시 행사", "DRAFT", at(15, 15));
        long closed = createEvent(token, "마감 행사", "OPEN", at(16, 15));
        long deleted = createEvent(token, "지운 행사", "OPEN", at(17, 15));

        Program closedEvent = programRepository.findById(closed).orElseThrow();
        ReflectionTestUtils.setField(closedEvent, "status", ProgramStatus.CLOSED);
        programRepository.save(closedEvent);
        Program deletedEvent = programRepository.findById(deleted).orElseThrow();
        ReflectionTestUtils.setField(deletedEvent, "deletedAt", LocalDateTime.now());
        programRepository.save(deletedEvent);

        mockMvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mockMvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + token)
                        .param("status", "OPEN").param("status", "CLOSED"))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + token).param("status", "DRAFT"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("임시 행사"));
    }

    @Test
    void 등록한_행사는_신청_방법이_바로_신청이다() throws Exception {
        String token = signUp("용인 주간보호센터");
        long eventId = createEvent(token, "행사", "OPEN", at(21, 13));

        assertThat(programRepository.findById(eventId).orElseThrow().getApplyMethod()).isEqualTo(ApplyMethod.DIRECT);
    }
}
