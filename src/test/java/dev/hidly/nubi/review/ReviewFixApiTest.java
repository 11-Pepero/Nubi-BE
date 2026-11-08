package dev.hidly.nubi.review;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewFixApiTest {
    private static final String PASSWORD = "Passw0rd!123";

    @Autowired
    private MockMvc mockMvc;

    private record Login(String email, String accessToken) {
    }

    private Login signUp() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@nubi.dev";
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgName":"용인 주간활동센터","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Login(email, JsonPath.read(response, "$.data.accessToken"));
    }

    private long id(ResultActions actions, String path) throws Exception {
        return ((Number) JsonPath.read(actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private long createEvent(Login host, int capacity) throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(10).withNano(0);
        return id(mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + host.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"정원 확인 행사","startAt":"%s","capacity":%d,"status":"OPEN"}
                        """.formatted(start, capacity))), "$.data.programId");
    }

    private long createGroup(Login login, int memberCount) throws Exception {
        return id(mockMvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"주간활동 A","memberCount":%d}
                        """.formatted(memberCount))), "$.data.id");
    }

    private ResultActions apply(Login login, long eventId, long groupId) throws Exception {
        return mockMvc.perform(post("/api/v1/programs/{programId}/applications", eventId)
                .header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"groupId":%d}
                        """.formatted(groupId)));
    }

    private ResultActions decide(Login host, long eventId, long applicationId, String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/events/{eventId}/applications/{applicationId}/status", eventId, applicationId)
                .header("Authorization", "Bearer " + host.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"%s"}
                        """.formatted(status)));
    }

    @Test
    void 대기_신청은_정원을_차지하지_않고_확정할_때_정원을_검사한다() throws Exception {
        Login host = signUp();
        Login first = signUp();
        Login second = signUp();
        long eventId = createEvent(host, 10);
        long firstApplication = id(apply(first, eventId, createGroup(first, 6)), "$.data.applicationId");

        apply(second, eventId, createGroup(second, 6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CAPACITY_EXCEEDED"));

        decide(host, eventId, firstApplication, "WAITING").andExpect(status().isOk());
        long secondApplication = id(apply(second, eventId, createGroup(second, 6)), "$.data.applicationId");
        decide(host, eventId, secondApplication, "CONFIRMED").andExpect(status().isOk());

        decide(host, eventId, firstApplication, "CONFIRMED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CAPACITY_EXCEEDED"));
    }

    @Test
    void 로그인을_5번_틀리면_잠기고_맞는_비밀번호도_429() throws Exception {
        Login login = signUp();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"wrong-password"}
                                    """.formatted(login.email())))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(login.email(), PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void 비밀번호가_72바이트를_넘으면_가입_400_로그인_401() throws Exception {
        String longPassword = "가".repeat(25);
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgName":"센터","type":"DAY_CARE","address":"용인시","email":"%s","password":"%s"}
                                """.formatted("test-" + UUID.randomUUID() + "@nubi.dev", longPassword)))
                .andExpect(status().isBadRequest());

        Login login = signUp();
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(login.email(), longPassword)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 목록에_null_원소가_있으면_400() throws Exception {
        Login login = signUp();
        mockMvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"그룹","memberCount":3,"supports":[null]}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/care-persons").header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"alias":"민들레","items":[null]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 마감이_시작보다_늦거나_지난_날짜로_공개하면_행사_등록_400() throws Exception {
        Login host = signUp();
        LocalDateTime start = LocalDateTime.now().plusDays(10).withNano(0);
        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"마감 오류","startAt":"%s","deadlineAt":"%s","status":"OPEN"}
                                """.formatted(start, start.plusDays(1))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"지난 행사","startAt":"%s","status":"OPEN"}
                                """.formatted(LocalDateTime.now().minusDays(1).withNano(0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 아주_큰_page를_보내도_500이_아니라_빈_목록() throws Exception {
        Login login = signUp();
        long groupId = createGroup(login, 3);
        mockMvc.perform(get("/api/v1/programs/eligible").header("Authorization", "Bearer " + login.accessToken())
                        .param("groupId", String.valueOf(groupId))
                        .param("page", "21474837")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }
}
