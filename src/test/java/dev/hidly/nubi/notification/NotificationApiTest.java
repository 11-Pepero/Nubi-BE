package dev.hidly.nubi.notification;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.notification.data.enums.NotificationTargetType;
import dev.hidly.nubi.notification.data.enums.NotificationType;
import dev.hidly.nubi.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificationService notificationService;

    private record Login(String accessToken, Long organizationId) {
    }

    private Login signUp() throws Exception {
        String body = """
                {"orgName":"용인 주간활동센터","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                """.formatted("test-" + UUID.randomUUID() + "@nubi.dev");
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number organizationId = JsonPath.read(response, "$.data.organizationId");
        return new Login(JsonPath.read(response, "$.data.accessToken"), organizationId.longValue());
    }

    private void notify(Long organizationId, String title) {
        notificationService.notify(organizationId, NotificationType.NEW_APPLICATION, title, "주간활동 A 그룹이 신청했어요.",
                NotificationTargetType.APPLICATION, 1L);
    }

    @Test
    void 내_기관_알림만_최신순으로_받는다() throws Exception {
        Login me = signUp();
        Login other = signUp();
        notify(me.organizationId(), "첫 번째 알림");
        notify(me.organizationId(), "두 번째 알림");
        notify(other.organizationId(), "다른 기관 알림");

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + me.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].title").value("두 번째 알림"))
                .andExpect(jsonPath("$.data.content[0].type").value("NEW_APPLICATION"))
                .andExpect(jsonPath("$.data.content[0].targetType").value("APPLICATION"))
                .andExpect(jsonPath("$.data.content[0].read").value(false));
    }

    @Test
    void 안_읽은_알림_수를_센다() throws Exception {
        Login me = signUp();
        notify(me.organizationId(), "알림 1");
        notify(me.organizationId(), "알림 2");
        notify(signUp().organizationId(), "다른 기관 알림");

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + me.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    private Number firstNotificationId(Login login) throws Exception {
        String list = mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + login.accessToken()))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(list, "$.data.content[0].notificationId");
    }

    @Test
    void 알림을_읽으면_안_읽은_수가_줄고_남의_알림은_못_읽는다() throws Exception {
        Login me = signUp();
        Login other = signUp();
        notify(me.organizationId(), "알림 1");
        notify(me.organizationId(), "알림 2");
        Number notificationId = firstNotificationId(me);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId).header("Authorization", "Bearer " + me.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + me.accessToken()))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId).header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void 모두_읽으면_내_기관_알림만_읽음이_된다() throws Exception {
        Login me = signUp();
        Login other = signUp();
        notify(me.organizationId(), "알림 1");
        notify(me.organizationId(), "알림 2");
        notify(other.organizationId(), "다른 기관 알림");

        mockMvc.perform(patch("/api/v1/notifications/read-all").header("Authorization", "Bearer " + me.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + me.accessToken()))
                .andExpect(jsonPath("$.data.content[0].read").value(true))
                .andExpect(jsonPath("$.data.content[1].read").value(true));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }
}
