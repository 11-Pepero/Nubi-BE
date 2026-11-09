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
import java.time.YearMonth;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 행사 관리 요약(API-40), 필요 지원 모아보기(API-48), 신청·결정 알림
@SpringBootTest
@AutoConfigureMockMvc
class EventDashboardApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SupportTagRepository supportTagRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostToken;
    private String suji;
    private String giheung;
    private long wheelchairId;
    private long easyWordsId;

    @BeforeEach
    void setUp() throws Exception {
        hostToken = signUp("용인 주간보호센터");
        suji = signUp("수지 주간보호센터");
        giheung = signUp("기흥 장애인복지관");
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
                {"orgName":"%s","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                """.formatted(orgName, "dash-" + UUID.randomUUID() + "@nubi.dev");
        return JsonPath.read(ok(mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.accessToken");
    }

    private String ok(ResultActions actions) throws Exception {
        return actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long readId(String response, String path) {
        return ((Number) JsonPath.read(response, path)).longValue();
    }

    private long createEvent(String title, String status, LocalDateTime startAt) throws Exception {
        String body = """
                {"title":"%s","status":"%s","startAt":"%s","capacity":40}
                """.formatted(title, status, startAt.withNano(0));
        return readId(ok(mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.programId");
    }

    private long createGroup(String token, String name, int memberCount, String supports) throws Exception {
        String body = "{\"name\":\"%s\",\"memberCount\":%d,\"supports\":[%s]}".formatted(name, memberCount, supports);
        return readId(ok(mockMvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.id");
    }

    private long createCarePerson(String token, String alias, String content) throws Exception {
        String body = "{\"alias\":\"%s\",\"applyToJudgement\":true,\"items\":[{\"content\":\"%s\"}]}".formatted(alias, content);
        return readId(ok(mockMvc.perform(post("/api/v1/care-persons").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.id");
    }

    private long apply(String token, long eventId, String body) throws Exception {
        return readId(ok(mockMvc.perform(post("/api/v1/programs/{id}/applications", eventId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.applicationId");
    }

    private void decide(long eventId, long applicationId, String status) throws Exception {
        ok(mockMvc.perform(patch("/api/v1/events/{e}/applications/{a}/status", eventId, applicationId)
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status))));
    }

    // 이번 달 안의 미래 시각 (월말이면 다음 달로 넘어가지 않게)
    private LocalDateTime laterThisMonth() {
        LocalDateTime soon = LocalDateTime.now().plusMinutes(30);
        LocalDateTime monthEnd = YearMonth.now().atEndOfMonth().atTime(23, 0);
        return soon.isBefore(monthEnd) ? soon : monthEnd;
    }

    @Test
    void 행사_관리_요약은_탭별_행사_수와_새_신청과_이번_달_확정_인원을_센다() throws Exception {
        long thisMonth = createEvent("이번 달 축제", "OPEN", laterThisMonth());
        long nextMonth = createEvent("다음 달 공방", "OPEN", LocalDateTime.now().plusMonths(2));
        createEvent("임시 행사", "DRAFT", LocalDateTime.now().plusDays(3));
        long ended = createEvent("끝난 행사", "OPEN", LocalDateTime.now().plusDays(3));
        jdbcTemplate.update("update program set status = 'ENDED' where id = ?", ended);

        long a = apply(suji, thisMonth, "{\"groupId\":%d,\"companionCount\":2}".formatted(createGroup(suji, "A", 6, "")));
        apply(giheung, thisMonth, "{\"groupId\":%d}".formatted(createGroup(giheung, "B", 4, "")));
        apply(suji, nextMonth, "{\"groupId\":%d}".formatted(createGroup(suji, "C", 5, "")));
        decide(thisMonth, a, "CONFIRMED");

        mockMvc.perform(get("/api/v1/events/summary").header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ongoingEventCount").value(2))
                .andExpect(jsonPath("$.data.pastEventCount").value(1))
                .andExpect(jsonPath("$.data.draftEventCount").value(1))
                .andExpect(jsonPath("$.data.newApplicationCount").value(2))
                .andExpect(jsonPath("$.data.upcomingParticipantCount").value(6))
                .andExpect(jsonPath("$.data.upcomingCompanionCount").value(2));

        mockMvc.perform(get("/api/v1/events/summary").header("Authorization", "Bearer " + suji))
                .andExpect(jsonPath("$.data.ongoingEventCount").value(0))
                .andExpect(jsonPath("$.data.newApplicationCount").value(0));
    }

    @Test
    void 필요_지원_모아보기는_항목별로_합치고_개별_케어와_오는_방법을_모은다() throws Exception {
        long eventId = createEvent("가을 축제", "OPEN", LocalDateTime.now().plusDays(10));
        long sujiGroup = createGroup(suji, "주간활동반", 6,
                "{\"supportTagId\":%d,\"quantity\":2},{\"supportTagId\":%d}".formatted(wheelchairId, easyWordsId));
        long giheungGroup = createGroup(giheung, "성인 여가반", 4, "{\"supportTagId\":%d,\"quantity\":3}".formatted(wheelchairId));
        long rejectedGroup = createGroup(giheung, "작업반", 5, "{\"supportTagId\":%d,\"quantity\":5}".formatted(wheelchairId));
        long careId = createCarePerson(suji, "하늘", "큰 소리 피하기");

        apply(suji, eventId, "{\"groupId\":%d,\"careIds\":[%d],\"companionCount\":2,\"transport\":\"리프트 차량\"}".formatted(sujiGroup, careId));
        long second = apply(giheung, eventId, "{\"groupId\":%d,\"transport\":\"대중교통\"}".formatted(giheungGroup));
        long rejected = apply(giheung, eventId, "{\"groupId\":%d}".formatted(rejectedGroup));
        decide(eventId, rejected, "REJECTED");
        jdbcTemplate.update("update application_support set prep_status = 'READY' where application_id = ?", second);

        mockMvc.perform(get("/api/v1/events/{id}/needs", eventId).header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity").value(40))
                .andExpect(jsonPath("$.data.participantCount").value(10))
                .andExpect(jsonPath("$.data.companionCount").value(2))
                // 쉬운 말 안내는 수량이 비어 있어 수지 이용자 6명 전체로 센다
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].code").value("EASY_WORDS"))
                .andExpect(jsonPath("$.data.items[0].totalQuantity").value(6))
                .andExpect(jsonPath("$.data.items[0].prepStatus").value("PENDING"))
                // 휠체어는 수지 2 + 기흥 3 (거절된 신청 5는 빠진다)
                .andExpect(jsonPath("$.data.items[1].code").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.data.items[1].totalQuantity").value(5))
                .andExpect(jsonPath("$.data.items[1].breakdown", hasSize(2)))
                .andExpect(jsonPath("$.data.items[1].breakdown[0].organizationName").value("수지 주간보호센터"))
                .andExpect(jsonPath("$.data.items[1].prepStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.readyItemCount").value(0))
                .andExpect(jsonPath("$.data.cares", hasSize(1)))
                .andExpect(jsonPath("$.data.cares[0].alias").value("하늘"))
                .andExpect(jsonPath("$.data.cares[0].organizationName").value("수지 주간보호센터"))
                .andExpect(jsonPath("$.data.arrivals", hasSize(2)))
                .andExpect(jsonPath("$.data.arrivals[0].transport").value("리프트 차량"))
                .andExpect(jsonPath("$.data.arrivals[1].transport").value("대중교통"));

        // 휠체어 지원을 모두 준비하면 그 항목은 READY
        jdbcTemplate.update("""
                update application_support set prep_status = 'READY'
                where support_tag_id = ? and application_id in (select id from application where program_id = ?)
                """, wheelchairId, eventId);
        mockMvc.perform(get("/api/v1/events/{id}/needs", eventId).header("Authorization", "Bearer " + hostToken))
                .andExpect(jsonPath("$.data.items[1].prepStatus").value("READY"))
                .andExpect(jsonPath("$.data.readyItemCount").value(1));

        mockMvc.perform(get("/api/v1/events/{id}/needs", eventId).header("Authorization", "Bearer " + suji))
                .andExpect(status().isForbidden());
    }

    @Test
    void 바로_신청하면_주최에게_확정하면_신청_기관에게_알림이_간다() throws Exception {
        long eventId = createEvent("가을 축제", "OPEN", LocalDateTime.now().plusDays(10));
        long applicationId = apply(suji, eventId, "{\"groupId\":%d}".formatted(createGroup(suji, "주간활동반", 6, "")));

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("NEW_APPLICATION"))
                .andExpect(jsonPath("$.data.content[0].targetType").value("APPLICATION"))
                .andExpect(jsonPath("$.data.content[0].targetId").value(applicationId))
                .andExpect(jsonPath("$.data.content[0].body").value("가을 축제에 수지 주간보호센터 6명이 신청했어요."));

        decide(eventId, applicationId, "CONFIRMED");
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + suji))
                .andExpect(jsonPath("$.data.content[0].type").value("APPLICATION_DECIDED"))
                .andExpect(jsonPath("$.data.content[0].title").value("신청이 확정됐어요"))
                .andExpect(jsonPath("$.data.content[0].targetId").value(applicationId));
    }
}
