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
class EventApplicationApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SupportTagRepository supportTagRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostToken;
    private String applicantToken;
    private String otherToken;
    private long wheelchairId;
    private long easyWordsId;

    @BeforeEach
    void setUp() throws Exception {
        hostToken = signUp("용인 주간보호센터");
        applicantToken = signUp("수지 주간보호센터");
        otherToken = signUp("기흥 장애인복지관");
        wheelchairId = tagId("WHEELCHAIR");
        easyWordsId = tagId("EASY_WORDS");
    }

    private String signUp(String orgName) throws Exception {
        String body = """
                {"orgName":"%s","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123","phone":"031-000-0000"}
                """.formatted(orgName, "host-" + UUID.randomUUID() + "@nubi.dev");
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

    private long readId(String response, String path) {
        return ((Number) JsonPath.read(response, path)).longValue();
    }

    private String ok(ResultActions actions) throws Exception {
        return actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long createEvent(String token, int capacity) throws Exception {
        String body = """
                {"title":"개관 30주년 가을 축제","status":"OPEN","startAt":"%s","capacity":%d}
                """.formatted(LocalDateTime.now().plusDays(10).withNano(0), capacity);
        return readId(ok(mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))), "$.data.programId");
    }

    private long createGroup(String token, String name, int memberCount) throws Exception {
        String body = """
                {"name":"%s","memberCount":%d,"supports":[{"supportTagId":%d,"quantity":2},{"supportTagId":%d}]}
                """.formatted(name, memberCount, wheelchairId, easyWordsId);
        return readId(ok(mockMvc.perform(post("/api/v1/groups")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))), "$.data.id");
    }

    private long createCarePerson(String token) throws Exception {
        String body = """
                {"alias":"하늘","applyToJudgement":true,"items":[{"content":"큰 소리 피하기"}]}
                """;
        return readId(ok(mockMvc.perform(post("/api/v1/care-persons")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))), "$.data.id");
    }

    private long apply(String token, long eventId, String body) throws Exception {
        return readId(ok(mockMvc.perform(post("/api/v1/programs/{programId}/applications", eventId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))), "$.data.applicationId");
    }

    private ResultActions decide(String token, long eventId, long applicationId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/events/{eventId}/applications/{applicationId}/status", eventId, applicationId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 주최_기관은_신청_기관_목록과_요약_숫자를_본다() throws Exception {
        long eventId = createEvent(hostToken, 40);
        long groupA = createGroup(applicantToken, "주간활동반", 6);
        long careId = createCarePerson(applicantToken);
        apply(applicantToken, eventId, "{\"groupId\":%d,\"careIds\":[%d],\"companionCount\":2}".formatted(groupA, careId));
        long groupB = createGroup(otherToken, "성인 여가반", 4);
        long second = apply(otherToken, eventId, "{\"groupId\":%d,\"companionCount\":2}".formatted(groupB));
        long groupC = createGroup(otherToken, "작업반", 3);
        long rejected = apply(otherToken, eventId, "{\"groupId\":%d,\"companionCount\":1}".formatted(groupC));
        decide(hostToken, eventId, second, "{\"status\":\"CONFIRMED\"}").andExpect(status().isOk());
        decide(hostToken, eventId, rejected, "{\"status\":\"REJECTED\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/events/{eventId}/applications", eventId).header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity").value(40))
                .andExpect(jsonPath("$.data.applicantOrgCount").value(2))
                .andExpect(jsonPath("$.data.participantCount").value(10))
                .andExpect(jsonPath("$.data.companionCount").value(4))
                .andExpect(jsonPath("$.data.newApplicationCount").value(1))
                .andExpect(jsonPath("$.data.statusCounts.SUBMITTED").value(1))
                .andExpect(jsonPath("$.data.statusCounts.CONFIRMED").value(1))
                .andExpect(jsonPath("$.data.statusCounts.REJECTED").value(1))
                .andExpect(jsonPath("$.data.applications.totalElements").value(3))
                .andExpect(jsonPath("$.data.applications.content[2].organizationName").value("수지 주간보호센터"))
                .andExpect(jsonPath("$.data.applications.content[2].groupName").value("주간활동반"))
                .andExpect(jsonPath("$.data.applications.content[2].participantCount").value(6))
                .andExpect(jsonPath("$.data.applications.content[2].companionCount").value(2))
                .andExpect(jsonPath("$.data.applications.content[2].supports", hasSize(2)))
                .andExpect(jsonPath("$.data.applications.content[2].careCount").value(1))
                .andExpect(jsonPath("$.data.applications.content[2].status").value("SUBMITTED"));

        mockMvc.perform(get("/api/v1/events/{eventId}/applications", eventId).param("status", "CONFIRMED")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(jsonPath("$.data.applications.totalElements").value(1))
                .andExpect(jsonPath("$.data.applications.content[0].applicationId").value(second));

        // 내 행사 목록에도 같은 숫자가 나온다 (거절된 신청 제외)
        mockMvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + hostToken))
                .andExpect(jsonPath("$.data.content[0].applicationOrgCount").value(2))
                .andExpect(jsonPath("$.data.content[0].participantCount").value(10))
                .andExpect(jsonPath("$.data.content[0].newApplicationCount").value(1));
    }

    @Test
    void 신청_내용_상세는_담당_연락처와_스냅샷과_문의를_보여준다() throws Exception {
        long eventId = createEvent(hostToken, 40);
        long groupId = createGroup(applicantToken, "주간활동반", 6);
        long careId = createCarePerson(applicantToken);
        long applicationId = apply(applicantToken, eventId, """
                {"groupId":%d,"careIds":[%d],"companionCount":2,"transport":"센터 차량 1대","message":"점심 식사가 포함인지 궁금해요."}
                """.formatted(groupId, careId));

        mockMvc.perform(get("/api/v1/events/{eventId}/applications/{applicationId}", eventId, applicationId)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.organizationName").value("수지 주간보호센터"))
                .andExpect(jsonPath("$.data.organizationPhone").value("031-000-0000"))
                .andExpect(jsonPath("$.data.groupName").value("주간활동반"))
                .andExpect(jsonPath("$.data.transport").value("센터 차량 1대"))
                .andExpect(jsonPath("$.data.supports", hasSize(2)))
                .andExpect(jsonPath("$.data.supports[0].prepStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.cares[0].alias").value("하늘"))
                .andExpect(jsonPath("$.data.cares[0].content").value("큰 소리 피하기"))
                .andExpect(jsonPath("$.data.messages", hasSize(1)))
                .andExpect(jsonPath("$.data.messages[0].fromHost").value(false))
                .andExpect(jsonPath("$.data.messages[0].body").value("점심 식사가 포함인지 궁금해요."));
    }

    @Test
    void 확정_대기_거절은_전이표대로만_바뀌고_사유는_주최_메시지로_남는다() throws Exception {
        long eventId = createEvent(hostToken, 40);
        long groupId = createGroup(applicantToken, "주간활동반", 6);
        long applicationId = apply(applicantToken, eventId, "{\"groupId\":%d}".formatted(groupId));

        decide(hostToken, eventId, applicationId, "{\"status\":\"WAITING\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.decidedAt").isEmpty());
        decide(hostToken, eventId, applicationId, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.decidedAt").isNotEmpty());
        // 같은 상태로는 바꿀 수 없다
        decide(hostToken, eventId, applicationId, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));
        // 주최가 정할 수 없는 상태
        decide(hostToken, eventId, applicationId, "{\"status\":\"ATTENDED\"}")
                .andExpect(status().isBadRequest());
        decide(hostToken, eventId, applicationId, "{\"status\":\"REJECTED\",\"reason\":\"정원이 차서 다음 회차로 안내드려요.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.messages", hasSize(1)))
                .andExpect(jsonPath("$.data.messages[0].fromHost").value(true))
                .andExpect(jsonPath("$.data.messages[0].body").value("정원이 차서 다음 회차로 안내드려요."));
        // 거절된 신청은 다시 바꿀 수 없다
        decide(hostToken, eventId, applicationId, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isBadRequest());

        jdbcTemplate.update("update application set status = 'CANCELLED' where id = ?", applicationId);
        decide(hostToken, eventId, applicationId, "{\"status\":\"WAITING\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 주최_기관이_아니면_403_다른_행사의_신청이면_404() throws Exception {
        long eventId = createEvent(hostToken, 40);
        long otherEventId = createEvent(hostToken, 40);
        long groupId = createGroup(applicantToken, "주간활동반", 6);
        long applicationId = apply(applicantToken, eventId, "{\"groupId\":%d}".formatted(groupId));

        // 신청한 기관도 주최 화면 API는 쓸 수 없다
        mockMvc.perform(get("/api/v1/events/{eventId}/applications", eventId).header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{eventId}/applications/{applicationId}", eventId, applicationId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        decide(applicantToken, eventId, applicationId, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/events/{eventId}/applications/{applicationId}", otherEventId, applicationId)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/events/{eventId}/applications", 999999999L).header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/events/{eventId}/applications", eventId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 상태_값이_없으면_400() throws Exception {
        long eventId = createEvent(hostToken, 40);
        long groupId = createGroup(applicantToken, "주간활동반", 6);
        long applicationId = apply(applicantToken, eventId, "{\"groupId\":%d}".formatted(groupId));

        decide(hostToken, eventId, applicationId, "{}").andExpect(status().isBadRequest());
        decide(hostToken, eventId, applicationId, "{\"status\":\"DONE\"}").andExpect(status().isBadRequest());
    }
}
