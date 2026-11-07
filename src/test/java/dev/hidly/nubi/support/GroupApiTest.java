package dev.hidly.nubi.support;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.organization.data.enums.MemberRole;
import dev.hidly.nubi.organization.data.repository.MemberRepository;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GroupApiTest {
    private static final String PASSWORD = "Passw0rd!123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupportTagRepository supportTagRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private long organizationId;
    private long wheelchairId;
    private long companionId;
    private long easyWordsId;

    @BeforeEach
    void setUp() throws Exception {
        String response = signUp();
        adminToken = JsonPath.read(response, "$.data.accessToken");
        organizationId = ((Number) JsonPath.read(response, "$.data.organizationId")).longValue();
        wheelchairId = tagId("WHEELCHAIR");
        companionId = tagId("COMPANION");
        easyWordsId = tagId("EASY_WORDS");
    }

    private long tagId(String code) {
        return supportTagRepository.findAll().stream()
                .filter(tag -> tag.getCode().equals(code))
                .map(SupportTag::getId)
                .findFirst().orElseThrow();
    }

    private String signUp() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgName":"용인 주간활동센터","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"%s"}
                                """.formatted("test-" + UUID.randomUUID() + "@nubi.dev", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String staffToken() throws Exception {
        String email = "staff-" + UUID.randomUUID() + "@nubi.dev";
        memberRepository.save(Member.builder()
                .organization(organizationRepository.findById(organizationId).orElseThrow())
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(MemberRole.STAFF)
                .build());
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    private String groupBody(String name, int memberCount, int wheelchair, int companion) {
        return """
                {"name":"%s","memberCount":%d,"supports":[
                  {"supportTagId":%d,"quantity":%d,"note":"접이식 2대"},
                  {"supportTagId":%d,"quantity":%d,"ratio":"1:2"},
                  {"supportTagId":%d}
                ]}
                """.formatted(name, memberCount, wheelchairId, wheelchair, companionId, companion, easyWordsId);
    }

    private ResultActions createGroup(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/groups")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long createGroupId(String body) throws Exception {
        String response = createGroup(adminToken, body)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    @Test
    void 지원_항목_사전은_분류별로_묶여서_나온다() throws Exception {
        mockMvc.perform(get("/api/v1/support-tags").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("MOBILITY"))
                .andExpect(jsonPath("$.data[0].label").value("이동"))
                .andExpect(jsonPath("$.data[0].tags[0].code").value("WHEELCHAIR"));

        mockMvc.perform(get("/api/v1/support-tags").param("category", "COMMUNICATION")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].category").value("COMMUNICATION"))
                .andExpect(jsonPath("$.data[0].label").value("의사소통"))
                .andExpect(jsonPath("$.data[0].tags[*].label",
                        containsInAnyOrder("쉬운 말 안내", "그림 안내판", "수어 통역")));
    }

    @Test
    void 지원_항목_사전은_로그인해야_볼_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/support-tags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 그룹을_만들고_상세와_목록에서_볼_수_있다() throws Exception {
        createGroup(adminToken, groupBody("주간활동 A", 10, 3, 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("주간활동 A"))
                .andExpect(jsonPath("$.data.memberCount").value(10))
                .andExpect(jsonPath("$.data.supports", hasSize(3)))
                .andExpect(jsonPath("$.data.supports[0].code").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.data.supports[0].quantity").value(3))
                .andExpect(jsonPath("$.data.supports[0].note").value("접이식 2대"))
                .andExpect(jsonPath("$.data.supports[1].ratio").value("1:2"));

        long groupId = createGroupId(groupBody("주간활동 B", 6, 1, 2));

        mockMvc.perform(get("/api/v1/groups/{id}", groupId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("주간활동 B"))
                .andExpect(jsonPath("$.data.supports", hasSize(3)));

        mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("주간활동 A"))
                .andExpect(jsonPath("$.data[1].name").value("주간활동 B"));
    }

    @Test
    void 그룹을_수정하면_공통_지원이_요청_내용으로_바뀐다() throws Exception {
        long groupId = createGroupId(groupBody("주간활동 A", 10, 3, 5));

        mockMvc.perform(put("/api/v1/groups/{id}", groupId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"주간활동 A반","memberCount":8,"supports":[{"supportTagId":%d,"quantity":4}]}
                                """.formatted(wheelchairId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("주간활동 A반"))
                .andExpect(jsonPath("$.data.memberCount").value(8))
                .andExpect(jsonPath("$.data.supports", hasSize(1)))
                .andExpect(jsonPath("$.data.supports[0].quantity").value(4));

        mockMvc.perform(get("/api/v1/groups/{id}", groupId).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.supports", hasSize(1)));
    }

    @Test
    void 기관_전체_요약은_인원을_적은_항목은_그_인원_아니면_그룹_인원_전체로_센다() throws Exception {
        createGroupId(groupBody("주간활동 A", 10, 3, 5));
        createGroupId(groupBody("주간활동 B", 6, 1, 2));

        mockMvc.perform(get("/api/v1/groups/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupCount").value(2))
                .andExpect(jsonPath("$.data.memberCount").value(16))
                .andExpect(jsonPath("$.data.supports", hasSize(3)))
                .andExpect(jsonPath("$.data.supports[0].code").value("EASY_WORDS"))
                .andExpect(jsonPath("$.data.supports[0].label").value("쉬운 말 안내"))
                .andExpect(jsonPath("$.data.supports[0].count").value(16))
                .andExpect(jsonPath("$.data.supports[1].code").value("COMPANION"))
                .andExpect(jsonPath("$.data.supports[1].count").value(7))
                .andExpect(jsonPath("$.data.supports[2].code").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.data.supports[2].count").value(4));
    }

    @Test
    void 그룹이_없으면_요약은_0이다() throws Exception {
        mockMvc.perform(get("/api/v1/groups/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupCount").value(0))
                .andExpect(jsonPath("$.data.memberCount").value(0))
                .andExpect(jsonPath("$.data.supports", hasSize(0)));
    }

    @Test
    void 관리자가_그룹을_삭제하면_목록과_상세에서_사라진다() throws Exception {
        long groupId = createGroupId(groupBody("주간활동 A", 10, 3, 5));

        mockMvc.perform(delete("/api/v1/groups/{id}", groupId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/groups/{id}", groupId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 일반_담당자는_그룹을_만들_수_있지만_삭제할_수_없다() throws Exception {
        String staffToken = staffToken();

        String response = createGroup(staffToken, groupBody("주간활동 A", 10, 3, 5))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number groupId = JsonPath.read(response, "$.data.id");

        mockMvc.perform(delete("/api/v1/groups/{id}", groupId).header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 다른_기관의_그룹은_보거나_고칠_수_없다() throws Exception {
        long groupId = createGroupId(groupBody("주간활동 A", 10, 3, 5));
        String otherToken = JsonPath.read(signUp(), "$.data.accessToken");

        mockMvc.perform(get("/api/v1/groups/{id}", groupId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/v1/groups/{id}", groupId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody("가로채기", 10, 1, 1)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 없는_지원_항목이면_404() throws Exception {
        createGroup(adminToken, """
                {"name":"주간활동 A","memberCount":10,"supports":[{"supportTagId":999999}]}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUPPORT_TAG_NOT_FOUND"));
    }

    @Test
    void 지원_항목이_중복되거나_필요_인원이_그룹_인원보다_많으면_400() throws Exception {
        createGroup(adminToken, """
                {"name":"주간활동 A","memberCount":10,"supports":[{"supportTagId":%d},{"supportTagId":%d}]}
                """.formatted(wheelchairId, wheelchairId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        createGroup(adminToken, groupBody("주간활동 A", 2, 3, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 이름이_없거나_인원이_0이거나_비율_형식이_틀리면_400() throws Exception {
        createGroup(adminToken, """
                {"name":"","memberCount":10}
                """).andExpect(status().isBadRequest());

        createGroup(adminToken, """
                {"name":"주간활동 A","memberCount":0}
                """).andExpect(status().isBadRequest());

        createGroup(adminToken, """
                {"name":"주간활동 A","memberCount":10,"supports":[{"supportTagId":%d,"ratio":"2명당 1명"}]}
                """.formatted(companionId)).andExpect(status().isBadRequest());
    }
}
