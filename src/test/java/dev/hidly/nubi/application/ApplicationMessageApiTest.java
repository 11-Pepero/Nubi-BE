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

// 지원 준비 체크(API-46), 답장(API-47), 문의(API-33), 신청 취소(API-32)
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationMessageApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SupportTagRepository supportTagRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostToken;
    private String applicantToken;
    private String otherToken;
    private long eventId;
    private long applicationId;

    @BeforeEach
    void setUp() throws Exception {
        hostToken = signUp("용인 주간보호센터");
        applicantToken = signUp("수지 주간보호센터");
        otherToken = signUp("기흥 장애인복지관");
        long wheelchairId = supportTagRepository.findAll().stream()
                .filter(tag -> tag.getCode().equals("WHEELCHAIR"))
                .map(SupportTag::getId).findFirst().orElseThrow();

        eventId = readId(ok(mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"가을 축제\",\"status\":\"OPEN\",\"startAt\":\"%s\"}"
                        .formatted(LocalDateTime.now().plusDays(10).withNano(0))))), "$.data.programId");
        long groupId = readId(ok(mockMvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + applicantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"주간활동반\",\"memberCount\":6,\"supports\":[{\"supportTagId\":%d,\"quantity\":2}]}"
                        .formatted(wheelchairId)))), "$.data.id");
        applicationId = readId(ok(mockMvc.perform(post("/api/v1/programs/{id}/applications", eventId)
                .header("Authorization", "Bearer " + applicantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":%d}".formatted(groupId)))), "$.data.applicationId");
    }

    private String signUp(String orgName) throws Exception {
        String body = """
                {"orgName":"%s","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                """.formatted(orgName, "msg-" + UUID.randomUUID() + "@nubi.dev");
        return JsonPath.read(ok(mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.accessToken");
    }

    private String ok(ResultActions actions) throws Exception {
        return actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long readId(String response, String path) {
        return ((Number) JsonPath.read(response, path)).longValue();
    }

    private ResultActions send(String method, String url, String token, String body, Object... vars) throws Exception {
        var builder = method.equals("PATCH") ? patch(url, vars) : post(url, vars);
        return mockMvc.perform(builder.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long supportId() throws Exception {
        return readId(ok(mockMvc.perform(get("/api/v1/applications/{id}", applicationId)
                .header("Authorization", "Bearer " + applicantToken))), "$.data.supports[0].applicationSupportId");
    }

    @Test
    void 주최_기관은_지원_준비_상태와_메모를_바꾸고_모아보기에_반영된다() throws Exception {
        long supportId = supportId();
        String url = "/api/v1/events/{e}/applications/{a}/supports/{s}";

        send("PATCH", url, hostToken, "{\"prepStatus\":\"READY\",\"hostNote\":\"앞줄 통로 쪽 2석\"}", eventId, applicationId, supportId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supports[0].prepStatus").value("READY"))
                .andExpect(jsonPath("$.data.supports[0].hostNote").value("앞줄 통로 쪽 2석"));

        mockMvc.perform(get("/api/v1/events/{id}/needs", eventId).header("Authorization", "Bearer " + hostToken))
                .andExpect(jsonPath("$.data.items[0].prepStatus").value("READY"))
                .andExpect(jsonPath("$.data.items[0].hostNotes[0]").value("앞줄 통로 쪽 2석"))
                .andExpect(jsonPath("$.data.readyItemCount").value(1));

        // 신청 기관이나 다른 기관은 바꿀 수 없다
        send("PATCH", url, applicantToken, "{\"prepStatus\":\"CHECK\"}", eventId, applicationId, supportId)
                .andExpect(status().isForbidden());
        // 이 신청의 지원이 아니면 404, 상태가 없으면 400
        send("PATCH", url, hostToken, "{\"prepStatus\":\"CHECK\"}", eventId, applicationId, 999999999L)
                .andExpect(status().isNotFound());
        send("PATCH", url, hostToken, "{}", eventId, applicationId, supportId)
                .andExpect(status().isBadRequest());
    }

    @Test
    void 문의를_보내면_문의_있음이_되고_주최가_답장한다() throws Exception {
        send("POST", "/api/v1/applications/{id}/messages", applicantToken, "{\"body\":\"점심 식사가 포함인가요?\"}", applicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INQUIRY"))
                .andExpect(jsonPath("$.data.messages", hasSize(1)))
                .andExpect(jsonPath("$.data.messages[0].fromHost").value(false));

        send("POST", "/api/v1/events/{e}/applications/{a}/messages", hostToken, "{\"body\":\"네, 도시락이 준비돼요.\"}", eventId, applicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INQUIRY"))
                .andExpect(jsonPath("$.data.messages", hasSize(2)))
                .andExpect(jsonPath("$.data.messages[1].fromHost").value(true))
                .andExpect(jsonPath("$.data.messages[1].body").value("네, 도시락이 준비돼요."));

        // 주최 기관 목록의 "문의 있음" 탭에 나온다
        mockMvc.perform(get("/api/v1/events/{id}/applications", eventId).param("status", "INQUIRY")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(jsonPath("$.data.applications.totalElements").value(1));

        // 확정된 신청에 문의해도 상태는 그대로
        send("PATCH", "/api/v1/events/{e}/applications/{a}/status", hostToken, "{\"status\":\"CONFIRMED\"}", eventId, applicationId)
                .andExpect(status().isOk());
        send("POST", "/api/v1/applications/{id}/messages", applicantToken, "{\"body\":\"주차 가능한가요?\"}", applicationId)
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void 남의_신청에는_문의나_답장을_보낼_수_없고_빈_본문은_400() throws Exception {
        send("POST", "/api/v1/applications/{id}/messages", otherToken, "{\"body\":\"문의\"}", applicationId)
                .andExpect(status().isForbidden());
        send("POST", "/api/v1/events/{e}/applications/{a}/messages", otherToken, "{\"body\":\"답장\"}", eventId, applicationId)
                .andExpect(status().isForbidden());
        send("POST", "/api/v1/applications/{id}/messages", applicantToken, "{\"body\":\" \"}", applicationId)
                .andExpect(status().isBadRequest());
    }

    @Test
    void 신청을_취소하면_사유가_남고_취소된_신청에는_문의할_수_없다() throws Exception {
        send("PATCH", "/api/v1/applications/{id}/cancel", applicantToken, "{\"reason\":\"이용자 컨디션이 좋지 않아요\"}", applicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.step").value("ENDED"))
                .andExpect(jsonPath("$.data.messages[0].body").value("이용자 컨디션이 좋지 않아요"));

        send("PATCH", "/api/v1/applications/{id}/cancel", applicantToken, "{}", applicationId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));
        send("POST", "/api/v1/applications/{id}/messages", applicantToken, "{\"body\":\"문의\"}", applicationId)
                .andExpect(status().isBadRequest());
        send("POST", "/api/v1/events/{e}/applications/{a}/messages", hostToken, "{\"body\":\"답장\"}", eventId, applicationId)
                .andExpect(status().isBadRequest());

        // 취소한 신청은 주최 쪽 숫자에서 빠진다
        mockMvc.perform(get("/api/v1/events/{id}/applications", eventId).header("Authorization", "Bearer " + hostToken))
                .andExpect(jsonPath("$.data.participantCount").value(0));
    }

    @Test
    void 행사가_시작했거나_남의_신청이면_취소할_수_없다() throws Exception {
        send("PATCH", "/api/v1/applications/{id}/cancel", otherToken, "{}", applicationId)
                .andExpect(status().isForbidden());

        jdbcTemplate.update("update program set start_at = ? where id = ?", LocalDateTime.now().minusHours(1), eventId);
        send("PATCH", "/api/v1/applications/{id}/cancel", applicantToken, "{}", applicationId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));
    }
}
