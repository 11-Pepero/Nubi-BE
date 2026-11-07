package dev.hidly.nubi.support;

import com.jayway.jsonpath.JsonPath;
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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CarePersonApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupportTagRepository supportTagRepository;

    private String token;
    private long powerWheelchairId;
    private long easyWordsId;

    @BeforeEach
    void setUp() throws Exception {
        token = signUpToken();
        powerWheelchairId = tagId("POWER_WHEELCHAIR");
        easyWordsId = tagId("EASY_WORDS");
    }

    private long tagId(String code) {
        return supportTagRepository.findAll().stream()
                .filter(tag -> tag.getCode().equals(code))
                .map(SupportTag::getId)
                .findFirst().orElseThrow();
    }

    private String signUpToken() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgName":"용인 주간활동센터","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                                """.formatted("test-" + UUID.randomUUID() + "@nubi.dev")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    private long createGroup(String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","memberCount":8}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private ResultActions createCarePerson(String accessToken, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/care-persons")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String carePersonBody(String alias, Long groupId) {
        return """
                {"alias":"%s","groupId":%s,"memo":"오후에 피로도가 높음","applyToJudgement":true,"items":[
                  {"supportTagId":%d,"content":"전동휠체어 충전 콘센트"},
                  {"supportTagId":%d},
                  {"content":"점심 후 30분 휴식"}
                ]}
                """.formatted(alias, groupId == null ? "null" : groupId, powerWheelchairId, easyWordsId);
    }

    private long createCarePersonId(String alias, Long groupId) throws Exception {
        String response = createCarePerson(token, carePersonBody(alias, groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    @Test
    void 개별_케어를_추가하고_상세를_볼_수_있다() throws Exception {
        long groupId = createGroup("주간활동 A");

        createCarePerson(token, carePersonBody("민들레", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alias").value("민들레"))
                .andExpect(jsonPath("$.data.groupId").value(groupId))
                .andExpect(jsonPath("$.data.groupName").value("주간활동 A"))
                .andExpect(jsonPath("$.data.applyToJudgement").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].code").value("POWER_WHEELCHAIR"))
                .andExpect(jsonPath("$.data.items[0].content").value("전동휠체어 충전 콘센트"))
                .andExpect(jsonPath("$.data.items[2].supportTagId").isEmpty())
                .andExpect(jsonPath("$.data.items[2].content").value("점심 후 30분 휴식"));

        long careId = createCarePersonId("해바라기", null);

        mockMvc.perform(get("/api/v1/care-persons/{id}", careId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alias").value("해바라기"))
                .andExpect(jsonPath("$.data.groupId").isEmpty())
                .andExpect(jsonPath("$.data.memo").value("오후에 피로도가 높음"));
    }

    @Test
    void 판정_반영_여부를_빼면_반영으로_저장된다() throws Exception {
        createCarePerson(token, """
                {"alias":"민들레","items":[{"content":"점심 후 휴식"}]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applyToJudgement").value(true));
    }

    @Test
    void 명단은_그룹_별칭_케어_종류로_거를_수_있다() throws Exception {
        long groupA = createGroup("주간활동 A");
        long groupB = createGroup("주간활동 B");
        createCarePersonId("민들레", groupA);
        createCarePersonId("해바라기", groupB);
        createCarePerson(token, """
                {"alias":"민트","groupId":%d,"items":[{"content":"점심 후 휴식"}]}
                """.formatted(groupA)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/care-persons").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(3)));

        mockMvc.perform(get("/api/v1/care-persons").param("groupId", String.valueOf(groupA))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get("/api/v1/care-persons").param("keyword", "민")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get("/api/v1/care-persons").param("category", "MOBILITY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].alias").value("민들레"))
                .andExpect(jsonPath("$.data[1].alias").value("해바라기"));

        mockMvc.perform(get("/api/v1/care-persons").param("groupId", String.valueOf(groupA)).param("category", "MOBILITY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].alias").value("민들레"));
    }

    @Test
    void 수정하면_케어_항목이_요청_내용으로_바뀌고_그룹은_유지된다() throws Exception {
        long groupId = createGroup("주간활동 A");
        long careId = createCarePersonId("민들레", groupId);

        mockMvc.perform(put("/api/v1/care-persons/{id}", careId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"alias":"민들레2","memo":null,"applyToJudgement":false,"items":[{"supportTagId":%d}]}
                                """.formatted(easyWordsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alias").value("민들레2"))
                .andExpect(jsonPath("$.data.groupId").value(groupId))
                .andExpect(jsonPath("$.data.memo").isEmpty())
                .andExpect(jsonPath("$.data.applyToJudgement").value(false))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].code").value("EASY_WORDS"));
    }

    @Test
    void 삭제하면_명단과_상세에서_사라진다() throws Exception {
        long careId = createCarePersonId("민들레", null);

        mockMvc.perform(delete("/api/v1/care-persons/{id}", careId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/care-persons/{id}", careId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CARE_PERSON_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/care-persons").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 그룹_목록과_상세에_개별_케어_수가_나온다() throws Exception {
        long groupA = createGroup("주간활동 A");
        long groupB = createGroup("주간활동 B");
        createCarePersonId("민들레", groupA);
        createCarePersonId("해바라기", groupA);
        long deletedCareId = createCarePersonId("민트", groupA);
        mockMvc.perform(delete("/api/v1/care-persons/{id}", deletedCareId).header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].careCount").value(2))
                .andExpect(jsonPath("$.data[1].careCount").value(0));

        mockMvc.perform(get("/api/v1/groups/{id}", groupA).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.careCount").value(2));

        mockMvc.perform(get("/api/v1/groups/{id}", groupB).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.careCount").value(0));
    }

    @Test
    void 그룹을_삭제하면_소속_개별_케어는_그룹_없음으로_남는다() throws Exception {
        long groupId = createGroup("주간활동 A");
        long careId = createCarePersonId("민들레", groupId);

        mockMvc.perform(delete("/api/v1/groups/{id}", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/care-persons/{id}", careId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").isEmpty())
                .andExpect(jsonPath("$.data.items", hasSize(3)));
    }

    @Test
    void 다른_기관의_개별_케어와_그룹에는_접근할_수_없다() throws Exception {
        long groupId = createGroup("주간활동 A");
        long careId = createCarePersonId("민들레", groupId);
        String otherToken = signUpToken();

        mockMvc.perform(get("/api/v1/care-persons/{id}", careId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/care-persons/{id}", careId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/care-persons").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.data", hasSize(0)));

        createCarePerson(otherToken, carePersonBody("가로채기", groupId))
                .andExpect(status().isForbidden());
    }

    @Test
    void 없는_그룹이나_지원_항목이면_404() throws Exception {
        createCarePerson(token, carePersonBody("민들레", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));

        createCarePerson(token, """
                {"alias":"민들레","items":[{"supportTagId":999999}]}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUPPORT_TAG_NOT_FOUND"));
    }

    @Test
    void 별칭이_없거나_너무_길거나_빈_케어_항목이면_400() throws Exception {
        createCarePerson(token, """
                {"alias":"","items":[]}
                """).andExpect(status().isBadRequest());

        createCarePerson(token, """
                {"alias":"%s"}
                """.formatted("가".repeat(21))).andExpect(status().isBadRequest());

        createCarePerson(token, """
                {"alias":"민들레","items":[{"content":"  "}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
