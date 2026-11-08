package dev.hidly.nubi.application;

import com.jayway.jsonpath.JsonPath;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MyApplicationApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SupportTagRepository supportTagRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostToken;
    private String applicantToken;
    private long wheelchairId;

    @BeforeEach
    void setUp() throws Exception {
        hostToken = signUp("용인 주간보호센터", "host");
        applicantToken = signUp("수지 주간보호센터", "applicant");
        wheelchairId = supportTagRepository.findAll().stream()
                .filter(tag -> tag.getCode().equals("WHEELCHAIR"))
                .map(SupportTag::getId).findFirst().orElseThrow();
    }

    private String signUp(String orgName, String prefix) throws Exception {
        String body = """
                {"orgName":"%s","type":"DAY_CARE","address":"용인시 수지구 포은대로 435","email":"%s","password":"Passw0rd!123","phone":"031-222-2222"}
                """.formatted(orgName, prefix + "-" + UUID.randomUUID() + "@nubi.dev");
        return JsonPath.read(ok(mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.accessToken");
    }

    private String ok(ResultActions actions) throws Exception {
        return actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long readId(String response, String path) {
        return ((Number) JsonPath.read(response, path)).longValue();
    }

    private long createEvent(String title, LocalDateTime startAt) throws Exception {
        String body = """
                {"title":"%s","status":"OPEN","startAt":"%s"}
                """.formatted(title, startAt.withNano(0));
        return readId(ok(mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.programId");
    }

    private long createGroup(String name) throws Exception {
        String body = """
                {"name":"%s","memberCount":6,"supports":[{"supportTagId":%d,"quantity":2}]}
                """.formatted(name, wheelchairId);
        return readId(ok(mockMvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + applicantToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.id");
    }

    private long apply(long eventId, long groupId, String extra) throws Exception {
        String body = "{\"groupId\":%d%s}".formatted(groupId, extra == null ? "" : "," + extra);
        return readId(ok(mockMvc.perform(post("/api/v1/programs/{id}/applications", eventId)
                .header("Authorization", "Bearer " + applicantToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.applicationId");
    }

    private void decide(long eventId, long applicationId, String status) throws Exception {
        ok(mockMvc.perform(patch("/api/v1/events/{e}/applications/{a}/status", eventId, applicationId)
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status))));
    }

    @Test
    void 내_신청_목록은_우리_기관_신청만_최근_순으로_단계와_함께_보여준다() throws Exception {
        long festival = createEvent("가을 축제", LocalDateTime.now().plusDays(20));
        long pottery = createEvent("도자기 공방", LocalDateTime.now().plusDays(5));
        long first = apply(festival, createGroup("주간활동 A"), "\"companionCount\":4");
        long second = apply(pottery, createGroup("주간활동 B"), null);
        decide(pottery, second, "CONFIRMED");

        mockMvc.perform(get("/api/v1/applications").header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].applicationId").value(second))
                .andExpect(jsonPath("$.data.content[0].programTitle").value("도자기 공방"))
                .andExpect(jsonPath("$.data.content[0].hostName").value("용인 주간보호센터"))
                .andExpect(jsonPath("$.data.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.content[0].step").value("ATTEND"))
                .andExpect(jsonPath("$.data.content[1].applicationId").value(first))
                .andExpect(jsonPath("$.data.content[1].groupName").value("주간활동 A"))
                .andExpect(jsonPath("$.data.content[1].participantCount").value(6))
                .andExpect(jsonPath("$.data.content[1].companionCount").value(4))
                .andExpect(jsonPath("$.data.content[1].channel").value("DIRECT"))
                .andExpect(jsonPath("$.data.content[1].step").value("SUBMIT"));

        mockMvc.perform(get("/api/v1/applications").param("status", "CONFIRMED")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // 주최 기관 쪽에서는 신청한 게 없다
        mockMvc.perform(get("/api/v1/applications").header("Authorization", "Bearer " + hostToken))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 내_신청_요약은_단계별_건수를_센다() throws Exception {
        long thisWeek = createEvent("이번 주 행사", LocalDateTime.now().plusMinutes(30));
        long later = createEvent("다음 달 행사", LocalDateTime.now().plusDays(40));
        long a = apply(thisWeek, createGroup("A"), null);
        long b = apply(later, createGroup("B"), null);
        long c = apply(later, createGroup("C"), null);
        long d = apply(later, createGroup("D"), null);
        decide(thisWeek, a, "CONFIRMED");
        decide(later, b, "WAITING");
        decide(later, c, "REJECTED");
        jdbcTemplate.update("update application set status = 'PREPARING' where id = ?", d);

        mockMvc.perform(get("/api/v1/applications/summary").header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.preparing").value(1))
                .andExpect(jsonPath("$.data.waiting").value(1))
                .andExpect(jsonPath("$.data.upcoming").value(1))
                .andExpect(jsonPath("$.data.attended").value(0))
                .andExpect(jsonPath("$.data.statusCounts.REJECTED").value(1));
    }

    @Test
    void 신청_상세는_우리_기관_신청만_볼_수_있다() throws Exception {
        long eventId = createEvent("가을 축제", LocalDateTime.now().plusDays(20));
        long applicationId = apply(eventId, createGroup("주간활동 A"), "\"transport\":\"기관 차량\",\"message\":\"휠체어 자리 부탁드려요\"");
        mockMvc.perform(patch("/api/v1/events/{e}/applications/{a}/status", eventId, applicationId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WAITING\",\"reason\":\"자리 확인 후 알려드릴게요\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/{id}", applicationId).header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.step").value("SUBMIT"))
                .andExpect(jsonPath("$.data.program.programId").value(eventId))
                .andExpect(jsonPath("$.data.program.hostName").value("용인 주간보호센터"))
                .andExpect(jsonPath("$.data.transport").value("기관 차량"))
                .andExpect(jsonPath("$.data.supports", hasSize(1)))
                .andExpect(jsonPath("$.data.messages", hasSize(2)))
                .andExpect(jsonPath("$.data.messages[0].fromHost").value(false))
                .andExpect(jsonPath("$.data.messages[1].fromHost").value(true))
                .andExpect(jsonPath("$.data.messages[1].body").value("자리 확인 후 알려드릴게요"));

        mockMvc.perform(get("/api/v1/applications/{id}", applicationId).header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/applications/{id}", 999999999L).header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/applications")).andExpect(status().isUnauthorized());
    }

    @Test
    void 내_기관_조회는_기관과_로그인한_담당자를_보여준다() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/me").header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수지 주간보호센터"))
                .andExpect(jsonPath("$.data.type").value("DAY_CARE"))
                .andExpect(jsonPath("$.data.district").value("SUJI"))
                .andExpect(jsonPath("$.data.phone").value("031-222-2222"))
                .andExpect(jsonPath("$.data.member.email").isNotEmpty())
                .andExpect(jsonPath("$.data.member.role").value("ADMIN"));

        mockMvc.perform(get("/api/v1/organizations/me")).andExpect(status().isUnauthorized());
    }
}
