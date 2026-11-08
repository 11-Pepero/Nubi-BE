package dev.hidly.nubi.application;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SupportTagRepository supportTagRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostToken;
    private String applicantToken;
    private long wheelchairId;
    private long easyWordsId;
    private long powerWheelchairId;

    @BeforeEach
    void setUp() throws Exception {
        hostToken = signUp("용인 주간보호센터");
        applicantToken = signUp("수지 장애인복지관");
        wheelchairId = tagId("WHEELCHAIR");
        easyWordsId = tagId("EASY_WORDS");
        powerWheelchairId = tagId("POWER_WHEELCHAIR");
    }

    private String signUp(String orgName) throws Exception {
        String body = """
                {"orgName":"%s","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                """.formatted(orgName, "apply-" + UUID.randomUUID() + "@nubi.dev");
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

    private long createEvent(String token, String status, Integer capacity) throws Exception {
        String body = """
                {"title":"함께 만드는 도자기 공방","status":"%s","startAt":"%s"%s}
                """.formatted(status, LocalDateTime.now().plusDays(10).withNano(0),
                capacity == null ? "" : ",\"capacity\":" + capacity);
        String response = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readId(response, "$.data.programId");
    }

    private long createGroup(String token, String name, int memberCount) throws Exception {
        String body = """
                {"name":"%s","memberCount":%d,"supports":[{"supportTagId":%d,"quantity":3},{"supportTagId":%d}]}
                """.formatted(name, memberCount, wheelchairId, easyWordsId);
        String response = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readId(response, "$.data.id");
    }

    private long createCarePerson(String token, String alias) throws Exception {
        String body = """
                {"alias":"%s","memo":"메모는 복사하지 않음","applyToJudgement":true,"items":[
                  {"supportTagId":%d,"content":"충전 콘센트 필요"},
                  {"content":"점심 후 30분 휴식"}
                ]}
                """.formatted(alias, powerWheelchairId);
        String response = mockMvc.perform(post("/api/v1/care-persons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readId(response, "$.data.id");
    }

    private ResultActions apply(String token, long programId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/programs/{programId}/applications", programId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 그룹으로_바로_신청하면_공통_지원과_개별_케어가_복사되고_접수_기다림이_된다() throws Exception {
        long eventId = createEvent(hostToken, "OPEN", 10);
        long groupId = createGroup(applicantToken, "주간활동 A", 8);
        long careId = createCarePerson(applicantToken, "해바라기");

        String response = apply(applicantToken, eventId, """
                {"groupId":%d,"careIds":[%d],"companionCount":2,"transport":"기관 차량","message":"휠체어 3대 자리 부탁드려요"}
                """.formatted(groupId, careId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.programId").value(eventId))
                .andExpect(jsonPath("$.data.groupName").value("주간활동 A"))
                .andExpect(jsonPath("$.data.participantCount").value(8))
                .andExpect(jsonPath("$.data.companionCount").value(2))
                .andExpect(jsonPath("$.data.transport").value("기관 차량"))
                .andExpect(jsonPath("$.data.channel").value("DIRECT"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.supports", hasSize(2)))
                .andExpect(jsonPath("$.data.supports[0].code").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.data.supports[0].quantity").value(3))
                .andExpect(jsonPath("$.data.supports[0].prepStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.supports[1].code").value("EASY_WORDS"))
                .andExpect(jsonPath("$.data.cares", hasSize(1)))
                .andExpect(jsonPath("$.data.cares[0].carePersonId").value(careId))
                .andExpect(jsonPath("$.data.cares[0].alias").value("해바라기"))
                .andExpect(jsonPath("$.data.cares[0].content").value("전동휠체어: 충전 콘센트 필요\n점심 후 30분 휴식"))
                .andReturn().getResponse().getContentAsString();

        long applicationId = readId(response, "$.data.applicationId");
        assertThat(jdbcTemplate.queryForObject(
                "select body from application_message where application_id = ?", String.class, applicationId))
                .isEqualTo("휠체어 3대 자리 부탁드려요");
    }

    @Test
    void 그룹을_고쳐도_지난_신청의_스냅샷은_그대로다() throws Exception {
        long eventId = createEvent(hostToken, "OPEN", null);
        long groupId = createGroup(applicantToken, "주간활동 A", 8);
        long applicationId = readId(apply(applicantToken, eventId, "{\"groupId\":%d}".formatted(groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.applicationId");

        mockMvc.perform(put("/api/v1/groups/{id}", groupId)
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"주간활동 A반\",\"memberCount\":5,\"supports\":[]}"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select participant_count from application where id = ?", Integer.class, applicationId)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from application_support where application_id = ?", Integer.class, applicationId)).isEqualTo(2);
    }

    @Test
    void 같은_그룹이_진행_중인_신청이_있으면_409() throws Exception {
        long eventId = createEvent(hostToken, "OPEN", null);
        long groupId = createGroup(applicantToken, "주간활동 A", 8);
        apply(applicantToken, eventId, "{\"groupId\":%d}".formatted(groupId)).andExpect(status().isOk());

        apply(applicantToken, eventId, "{\"groupId\":%d}".formatted(groupId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APPLICATION_DUPLICATED"));
    }

    @Test
    void 정원은_거절_취소를_뺀_신청의_이용자_수로만_센다() throws Exception {
        long eventId = createEvent(hostToken, "OPEN", 10);
        long groupA = createGroup(applicantToken, "주간활동 A", 6);
        long groupB = createGroup(applicantToken, "주간활동 B", 5);
        long groupC = createGroup(applicantToken, "주간활동 C", 4);
        long first = readId(apply(applicantToken, eventId, "{\"groupId\":%d,\"companionCount\":5}".formatted(groupA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.applicationId");

        // 6 + 5 = 11명은 정원 10명을 넘는다 (동반 인력 5명은 세지 않음)
        apply(applicantToken, eventId, "{\"groupId\":%d}".formatted(groupB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CAPACITY_EXCEEDED"));
        // 6 + 4 = 10명은 딱 맞는다
        apply(applicantToken, eventId, "{\"groupId\":%d}".formatted(groupC)).andExpect(status().isOk());

        // 첫 신청이 취소되면 자리가 다시 난다
        jdbcTemplate.update("update application set status = 'CANCELLED' where id = ?", first);
        apply(applicantToken, eventId, "{\"groupId\":%d}".formatted(groupB)).andExpect(status().isOk());
    }

    @Test
    void 신청할_수_없는_행사는_400() throws Exception {
        long groupId = createGroup(applicantToken, "주간활동 A", 8);
        String body = "{\"groupId\":%d}".formatted(groupId);

        long draft = createEvent(hostToken, "DRAFT", null);
        apply(applicantToken, draft, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_ALLOWED"));

        long pastDeadline = createEvent(hostToken, "OPEN", null);
        Program event = programRepository.findById(pastDeadline).orElseThrow();
        ReflectionTestUtils.setField(event, "deadlineAt", LocalDateTime.now().minusDays(1));
        programRepository.save(event);
        apply(applicantToken, pastDeadline, body).andExpect(status().isBadRequest());

        long closed = createEvent(hostToken, "OPEN", null);
        Program closedEvent = programRepository.findById(closed).orElseThrow();
        ReflectionTestUtils.setField(closedEvent, "status", ProgramStatus.CLOSED);
        programRepository.save(closedEvent);
        apply(applicantToken, closed, body).andExpect(status().isBadRequest());

        // 내 기관 행사에는 신청할 수 없다
        long own = createEvent(applicantToken, "OPEN", null);
        apply(applicantToken, own, body).andExpect(status().isBadRequest());
    }

    @Test
    void 다른_기관의_그룹이나_개별_케어로는_신청할_수_없다() throws Exception {
        long eventId = createEvent(hostToken, "OPEN", null);
        long myGroup = createGroup(applicantToken, "주간활동 A", 8);
        long othersGroup = createGroup(hostToken, "남의 그룹", 3);
        long othersCare = createCarePerson(hostToken, "남의 케어");

        apply(applicantToken, eventId, "{\"groupId\":%d}".formatted(othersGroup))
                .andExpect(status().isForbidden());
        apply(applicantToken, eventId, "{\"groupId\":%d,\"careIds\":[%d]}".formatted(myGroup, othersCare))
                .andExpect(status().isForbidden());
    }

    @Test
    void 없는_행사_그룹_개별_케어는_404() throws Exception {
        long eventId = createEvent(hostToken, "OPEN", null);
        long groupId = createGroup(applicantToken, "주간활동 A", 8);

        apply(applicantToken, 999999999L, "{\"groupId\":%d}".formatted(groupId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"));
        apply(applicantToken, eventId, "{\"groupId\":999999999}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
        apply(applicantToken, eventId, "{\"groupId\":%d,\"careIds\":[999999999]}".formatted(groupId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CARE_PERSON_NOT_FOUND"));
    }

    @Test
    void 요청_값이_잘못되면_400_로그인하지_않으면_401() throws Exception {
        long eventId = createEvent(hostToken, "OPEN", null);
        long groupId = createGroup(applicantToken, "주간활동 A", 8);

        apply(applicantToken, eventId, "{}").andExpect(status().isBadRequest());
        apply(applicantToken, eventId, "{\"groupId\":%d,\"participantCount\":0}".formatted(groupId))
                .andExpect(status().isBadRequest());
        apply(applicantToken, eventId, "{\"groupId\":%d,\"companionCount\":-1}".formatted(groupId))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/programs/{programId}/applications", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":%d}".formatted(groupId)))
                .andExpect(status().isUnauthorized());
    }
}
