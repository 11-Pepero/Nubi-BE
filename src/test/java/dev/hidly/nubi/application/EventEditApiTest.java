package dev.hidly.nubi.application;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.application.service.ApplicationStatusScheduler;
import dev.hidly.nubi.program.data.enums.ConditionSource;
import dev.hidly.nubi.program.data.repository.ProgramConditionRepository;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 행사 수정(API-41), 신청 마감(API-42), 내 기관 수정(API-5), 다녀왔어요 자동 전환
@SpringBootTest
@AutoConfigureMockMvc
class EventEditApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SupportTagRepository supportTagRepository;
    @Autowired
    private ProgramConditionRepository programConditionRepository;
    @Autowired
    private ApplicationStatusScheduler applicationStatusScheduler;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostToken;
    private String applicantToken;
    private long wheelchairId;
    private long easyWordsId;

    @BeforeEach
    void setUp() throws Exception {
        hostToken = signUp("용인 주간보호센터");
        applicantToken = signUp("수지 주간보호센터");
        wheelchairId = tagId("WHEELCHAIR");
        easyWordsId = tagId("EASY_WORDS");
    }

    private long tagId(String code) {
        return supportTagRepository.findAll().stream()
                .filter(tag -> tag.getCode().equals(code))
                .map(SupportTag::getId).findFirst().orElseThrow();
    }

    private String signUp(String orgName) throws Exception {
        String body = """
                {"orgName":"%s","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123","phone":"031-111-1111"}
                """.formatted(orgName, "edit-" + UUID.randomUUID() + "@nubi.dev");
        return JsonPath.read(ok(mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.accessToken");
    }

    private String ok(ResultActions actions) throws Exception {
        return actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long readId(String response, String path) {
        return ((Number) JsonPath.read(response, path)).longValue();
    }

    private String eventBody(String title, String status, int capacity, String conditions) {
        return """
                {"title":"%s","status":"%s","startAt":"%s","capacity":%d,"conditions":[%s]}
                """.formatted(title, status, LocalDateTime.now().plusDays(10).withNano(0), capacity, conditions);
    }

    private long createEvent(String status, int capacity) throws Exception {
        return readId(ok(mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("가을 축제", status, capacity, "{\"supportTagId\":%d}".formatted(wheelchairId))))),
                "$.data.programId");
    }

    private long apply(long eventId, int memberCount) throws Exception {
        long groupId = readId(ok(mockMvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + applicantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"주간활동반\",\"memberCount\":%d,\"supports\":[]}".formatted(memberCount)))), "$.data.id");
        return readId(ok(mockMvc.perform(post("/api/v1/programs/{id}/applications", eventId)
                .header("Authorization", "Bearer " + applicantToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"groupId\":%d}".formatted(groupId)))), "$.data.applicationId");
    }

    private ResultActions update(String token, long eventId, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/events/{id}", eventId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 행사를_수정하면_정보와_주최_조건이_요청대로_바뀐다() throws Exception {
        long eventId = createEvent("DRAFT", 40);
        // 공고에서 AI가 뽑은 조건은 행사 수정으로 지워지지 않는다
        jdbcTemplate.update("""
                insert into program_condition (program_id, support_tag_id, status, source, created_at, updated_at)
                values (?, ?, 'UNKNOWN', 'AI', now(), now())
                """, eventId, tagId("SIGN_LANGUAGE"));

        update(hostToken, eventId, eventBody("개관 30주년 가을 축제", "OPEN", 30, "{\"supportTagId\":%d}".formatted(easyWordsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("개관 30주년 가을 축제"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.capacity").value(30))
                .andExpect(jsonPath("$.data.applyMethod").value("DIRECT"));

        var conditions = programConditionRepository.findAllByProgramIdOrderByIdAsc(eventId);
        assertThat(conditions).hasSize(2);
        assertThat(conditions).filteredOn(c -> c.getSource() == ConditionSource.HOST).hasSize(1);
        assertThat(conditions).filteredOn(c -> c.getSource() == ConditionSource.AI).hasSize(1);
    }

    @Test
    void 행사_수정은_주최만_끝난_행사나_임시_저장으로_되돌리기는_안_된다() throws Exception {
        long eventId = createEvent("OPEN", 40);
        String body = eventBody("가을 축제", "OPEN", 40, "");

        update(applicantToken, eventId, body).andExpect(status().isForbidden());
        update(hostToken, eventId, eventBody("가을 축제", "DRAFT", 40, "")).andExpect(status().isBadRequest());

        // 이미 신청한 6명보다 정원을 줄일 수 없다
        apply(eventId, 6);
        update(hostToken, eventId, eventBody("가을 축제", "OPEN", 5, ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CAPACITY_EXCEEDED"));
        update(hostToken, eventId, eventBody("가을 축제", "OPEN", 6, "")).andExpect(status().isOk());

        jdbcTemplate.update("update program set status = 'ENDED' where id = ?", eventId);
        update(hostToken, eventId, body).andExpect(status().isBadRequest());
    }

    @Test
    void 신청을_마감하면_더_이상_바로_신청을_받지_않는다() throws Exception {
        long eventId = createEvent("OPEN", 40);

        mockMvc.perform(patch("/api/v1/events/{id}/close", eventId).header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/events/{id}/close", eventId).header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        mockMvc.perform(patch("/api/v1/events/{id}/close", eventId).header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isBadRequest());

        long groupId = readId(ok(mockMvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + applicantToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A\",\"memberCount\":3,\"supports\":[]}"))), "$.data.id");
        mockMvc.perform(post("/api/v1/programs/{id}/applications", eventId).header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"groupId\":%d}".formatted(groupId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_ALLOWED"));
    }

    @Test
    void 관리자는_내_기관_정보를_고치고_주소를_바꾸면_구도_바뀐다() throws Exception {
        mockMvc.perform(patch("/api/v1/organizations/me").header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"용인 해솔 주간보호센터\",\"address\":\"용인시 수지구 포은대로 435\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("용인 해솔 주간보호센터"))
                .andExpect(jsonPath("$.data.district").value("SUJI"))
                .andExpect(jsonPath("$.data.phone").value("031-111-1111"));

        mockMvc.perform(get("/api/v1/organizations/me").header("Authorization", "Bearer " + hostToken))
                .andExpect(jsonPath("$.data.name").value("용인 해솔 주간보호센터"));

        mockMvc.perform(patch("/api/v1/organizations/me").header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자가_아닌_담당자는_기관_정보를_고칠_수_없다() throws Exception {
        String email = JsonPath.read(ok(mockMvc.perform(get("/api/v1/organizations/me")
                .header("Authorization", "Bearer " + applicantToken))), "$.data.member.email");
        jdbcTemplate.update("update member set role = 'STAFF' where email = ?", email);

        mockMvc.perform(patch("/api/v1/organizations/me").header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"031-999-9999\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 행사가_끝나면_확정_신청만_다녀왔어요로_바뀐다() throws Exception {
        long eventId = createEvent("OPEN", 40);
        long confirmed = apply(eventId, 3);
        long waiting = apply(eventId, 2);
        mockMvc.perform(patch("/api/v1/events/{e}/applications/{a}/status", eventId, confirmed)
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());

        // 아직 시작 전이면 그대로
        applicationStatusScheduler.markAttended(LocalDateTime.now());
        assertThat(statusOf(confirmed)).isEqualTo("CONFIRMED");

        jdbcTemplate.update("update program set start_at = ?, end_at = ? where id = ?",
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1), eventId);
        applicationStatusScheduler.markAttended(LocalDateTime.now());
        assertThat(statusOf(confirmed)).isEqualTo("ATTENDED");
        assertThat(statusOf(waiting)).isEqualTo("SUBMITTED");

        mockMvc.perform(get("/api/v1/applications/summary").header("Authorization", "Bearer " + applicantToken))
                .andExpect(jsonPath("$.data.attended").value(1));
        mockMvc.perform(get("/api/v1/applications/{id}", confirmed).header("Authorization", "Bearer " + applicantToken))
                .andExpect(jsonPath("$.data.step").value("REVIEW"));
    }

    private String statusOf(long applicationId) {
        return jdbcTemplate.queryForObject("select status from application where id = ?", String.class, applicationId);
    }
}
