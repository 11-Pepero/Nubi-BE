package dev.hidly.nubi.application;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ApplyMethod;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
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
class PrepareApplicationApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private SupportTagRepository supportTagRepository;

    private String token;
    private String otherToken;
    private long wheelchairId;

    @BeforeEach
    void setUp() throws Exception {
        token = signUp();
        otherToken = signUp();
        wheelchairId = supportTagRepository.findAll().stream()
                .filter(tag -> tag.getCode().equals("WHEELCHAIR"))
                .map(SupportTag::getId).findFirst().orElseThrow();
    }

    private String signUp() throws Exception {
        String body = """
                {"orgName":"용인 주간보호센터","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                """.formatted("prepare-" + UUID.randomUUID() + "@nubi.dev");
        return JsonPath.read(ok(mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.accessToken");
    }

    private String ok(ResultActions actions) throws Exception {
        return actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long readId(String response, String path) {
        return ((Number) JsonPath.read(response, path)).longValue();
    }

    // 공공데이터에서 들어온 외부 공고 (주최 기관 없음)
    private Program externalNotice(ApplyMethod method, ProgramStatus status, LocalDateTime deadlineAt) {
        return programRepository.save(Program.builder()
                .title("가족과 함께하는 그림책 낭독회")
                .hostName("용인시 문화재단")
                .category(ProgramCategory.CULTURE)
                .applyMethod(method)
                .applyUrl(method == ApplyMethod.ONLINE ? "https://example.org/apply" : null)
                .contactPhone("031-000-0000")
                .startAt(LocalDateTime.now().plusDays(7))
                .deadlineAt(deadlineAt)
                .status(status)
                .build());
    }

    private long createGroup(String bearer) throws Exception {
        String body = """
                {"name":"주간활동 A","memberCount":8,"supports":[{"supportTagId":%d,"quantity":3}]}
                """.formatted(wheelchairId);
        return readId(ok(mockMvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.id");
    }

    private long createCarePerson() throws Exception {
        return readId(ok(mockMvc.perform(post("/api/v1/care-persons").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\":\"바다\",\"applyToJudgement\":true,\"items\":[{\"content\":\"조용한 자리\"}]}"))), "$.data.id");
    }

    private ResultActions prepare(String bearer, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/applications/prepare").header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions submitted(String bearer, long applicationId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/applications/{id}/submitted", applicationId)
                .header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 외부_공고에_신청_준비를_만들면_준비_중이고_접수_채널을_돌려준다() throws Exception {
        Program notice = externalNotice(ApplyMethod.PHONE, ProgramStatus.OPEN, LocalDateTime.now().plusDays(3));
        long groupId = createGroup(token);
        long careId = createCarePerson();

        prepare(token, """
                {"programId":%d,"groupId":%d,"careIds":[%d],"companionCount":4}
                """.formatted(notice.getId(), groupId, careId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.status").value("PREPARING"))
                .andExpect(jsonPath("$.data.application.channel").value("PHONE"))
                .andExpect(jsonPath("$.data.application.submittedAt").isEmpty())
                .andExpect(jsonPath("$.data.application.participantCount").value(8))
                .andExpect(jsonPath("$.data.application.companionCount").value(4))
                .andExpect(jsonPath("$.data.application.supports", hasSize(1)))
                .andExpect(jsonPath("$.data.application.cares[0].alias").value("바다"))
                .andExpect(jsonPath("$.data.channel.applyMethod").value("PHONE"))
                .andExpect(jsonPath("$.data.channel.hostName").value("용인시 문화재단"))
                .andExpect(jsonPath("$.data.channel.contactPhone").value("031-000-0000"))
                .andExpect(jsonPath("$.data.channel.deadlineAt").isNotEmpty());
    }

    @Test
    void 접수_완료를_누르면_접수_기다림이_되고_다시_누를_수_없다() throws Exception {
        Program notice = externalNotice(ApplyMethod.ONLINE, ProgramStatus.OPEN, null);
        long groupId = createGroup(token);
        long applicationId = readId(ok(prepare(token, "{\"programId\":%d,\"groupId\":%d}".formatted(notice.getId(), groupId))),
                "$.data.application.applicationId");

        // 다른 기관은 내 신청을 접수 완료로 바꿀 수 없다
        submitted(otherToken, applicationId, "{}").andExpect(status().isForbidden());

        submitted(token, applicationId, "{\"channel\":\"PHONE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.step").value("SUBMIT"))
                .andExpect(jsonPath("$.data.channel").value("PHONE"))
                .andExpect(jsonPath("$.data.submittedAt").isNotEmpty());

        submitted(token, applicationId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));

        mockMvc.perform(get("/api/v1/applications/summary").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.preparing").value(0))
                .andExpect(jsonPath("$.data.waiting").value(1));
    }

    @Test
    void 바로_신청_행사나_마감된_공고는_신청_준비를_만들_수_없다() throws Exception {
        long groupId = createGroup(token);
        String body = "{\"programId\":%d,\"groupId\":%d}";

        Program direct = externalNotice(ApplyMethod.DIRECT, ProgramStatus.OPEN, null);
        prepare(token, body.formatted(direct.getId(), groupId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_ALLOWED"));

        Program closed = externalNotice(ApplyMethod.PHONE, ProgramStatus.CLOSED, null);
        prepare(token, body.formatted(closed.getId(), groupId)).andExpect(status().isBadRequest());

        Program pastDeadline = externalNotice(ApplyMethod.PAPER, ProgramStatus.OPEN, LocalDateTime.now().minusDays(1));
        prepare(token, body.formatted(pastDeadline.getId(), groupId)).andExpect(status().isBadRequest());
    }

    @Test
    void 같은_그룹의_진행_중_신청이_있으면_409_남의_그룹이면_403() throws Exception {
        Program notice = externalNotice(ApplyMethod.PHONE, ProgramStatus.OPEN, null);
        long groupId = createGroup(token);
        ok(prepare(token, "{\"programId\":%d,\"groupId\":%d}".formatted(notice.getId(), groupId)));

        prepare(token, "{\"programId\":%d,\"groupId\":%d}".formatted(notice.getId(), groupId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APPLICATION_DUPLICATED"));

        long othersGroup = createGroup(otherToken);
        prepare(token, "{\"programId\":%d,\"groupId\":%d}".formatted(notice.getId(), othersGroup))
                .andExpect(status().isForbidden());
    }

    @Test
    void 잘못된_요청은_400_없는_신청은_404() throws Exception {
        Program notice = externalNotice(ApplyMethod.PHONE, ProgramStatus.OPEN, null);
        long groupId = createGroup(token);
        long applicationId = readId(ok(prepare(token, "{\"programId\":%d,\"groupId\":%d}".formatted(notice.getId(), groupId))),
                "$.data.application.applicationId");

        prepare(token, "{\"groupId\":%d}".formatted(groupId)).andExpect(status().isBadRequest());
        submitted(token, applicationId, "{\"channel\":\"DIRECT\"}").andExpect(status().isBadRequest());
        submitted(token, applicationId, "{\"submittedAt\":\"%s\"}".formatted(LocalDateTime.now().plusDays(1).withNano(0)))
                .andExpect(status().isBadRequest());
        submitted(token, 999999999L, "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("APPLICATION_NOT_FOUND"));
    }
}
