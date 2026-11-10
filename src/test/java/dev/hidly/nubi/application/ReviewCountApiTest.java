package dev.hidly.nubi.application;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 후기 수 연결: 내 신청 요약(API-30) "후기 n건 남기기", 내 신청 목록(API-29) reviewId,
// 행사 관리 요약(API-40) "받은 후기", 내 행사 목록(API-39) 후기 수·평균 별점
@SpringBootTest
@AutoConfigureMockMvc
class ReviewCountApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ApplicationRepository applicationRepository;

    private record Login(String token, Long organizationId) {
    }

    private Login host;
    private Login suji;
    private Login giheung;

    @BeforeEach
    void setUp() throws Exception {
        host = signUp("용인 주간보호센터");
        suji = signUp("수지 주간보호센터");
        giheung = signUp("기흥 장애인복지관");
    }

    private Login signUp(String orgName) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgName":"%s","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                                """.formatted(orgName, "review-count-" + UUID.randomUUID() + "@nubi.dev")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Login(JsonPath.read(response, "$.data.accessToken"),
                ((Number) JsonPath.read(response, "$.data.organizationId")).longValue());
    }

    // 행사 등록 API는 지난 날짜를 막아서, 이미 시작한(또는 앞으로 할) 행사와 신청은 저장소로 바로 만든다.
    private Program event(String title, ProgramStatus status, LocalDateTime startAt) {
        return programRepository.save(Program.builder()
                .hostOrganization(organizationRepository.getReferenceById(host.organizationId()))
                .title(title).status(status).startAt(startAt).build());
    }

    private Application application(Program program, Login applicant, ApplicationStatus status) {
        return applicationRepository.save(Application.builder()
                .program(program)
                .applicantOrganization(organizationRepository.getReferenceById(applicant.organizationId()))
                .participantCount(6).companionCount(2).status(status).build());
    }

    private long writeReview(Login login, Application application, int rating) throws Exception {
        String response = mockMvc.perform(post("/api/v1/applications/{id}/review", application.getId())
                        .header("Authorization", "Bearer " + login.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":%d,\"isPublic\":false}".formatted(rating)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.reviewId")).longValue();
    }

    @Test
    void 내_신청_요약은_후기를_쓸_수_있는데_안_쓴_신청만_센다() throws Exception {
        Program past = event("가을 사진 산책", ProgramStatus.ENDED, LocalDateTime.now().minusDays(3));
        Program pastToo = event("텃밭 가꾸기", ProgramStatus.ENDED, LocalDateTime.now().minusDays(10));
        Program future = event("겨울 축제", ProgramStatus.OPEN, LocalDateTime.now().plusDays(10));
        Program deleted = event("지운 행사", ProgramStatus.ENDED, LocalDateTime.now().minusDays(5));
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        programRepository.save(deleted);

        Application written = application(past, suji, ApplicationStatus.CONFIRMED);   // 후기 씀 → 빠짐
        Application attended = application(pastToo, suji, ApplicationStatus.ATTENDED); // 남기기 대상
        application(past, suji, ApplicationStatus.CANCELLED);                          // 취소 → 빠짐
        application(future, suji, ApplicationStatus.CONFIRMED);                        // 아직 안 함 → 빠짐
        application(deleted, suji, ApplicationStatus.ATTENDED);                        // 삭제된 행사 → 빠짐
        application(past, giheung, ApplicationStatus.ATTENDED);                        // 다른 기관 → 빠짐

        mockMvc.perform(get("/api/v1/applications/summary").header("Authorization", "Bearer " + suji.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewPending").value(2));

        long reviewId = writeReview(suji, written, 4);

        mockMvc.perform(get("/api/v1/applications/summary").header("Authorization", "Bearer " + suji.token()))
                .andExpect(jsonPath("$.data.reviewPending").value(1));

        // 목록 한 줄마다 쓴 후기 ID (안 썼으면 null)
        mockMvc.perform(get("/api/v1/applications").header("Authorization", "Bearer " + suji.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.applicationId == %d)].reviewId".formatted(written.getId()))
                        .value((int) reviewId))
                .andExpect(jsonPath("$.data.content[?(@.applicationId == %d)].reviewId".formatted(attended.getId()))
                        .value(contains(nullValue())));
    }

    @Test
    void 행사_관리_요약과_내_행사_목록에_받은_후기_수와_평균_별점이_나온다() throws Exception {
        Program past = event("여름 텃밭 가꾸기", ProgramStatus.ENDED, LocalDateTime.now().minusDays(20));
        Program noReview = event("어울림 합창", ProgramStatus.ENDED, LocalDateTime.now().minusDays(30));
        writeReview(suji, application(past, suji, ApplicationStatus.ATTENDED), 5);
        writeReview(giheung, application(past, giheung, ApplicationStatus.CONFIRMED), 4);

        // 다른 기관이 주최한 행사의 후기는 안 센다
        Program other = programRepository.save(Program.builder()
                .hostOrganization(organizationRepository.getReferenceById(suji.organizationId()))
                .title("수지 행사").status(ProgramStatus.ENDED).startAt(LocalDateTime.now().minusDays(2)).build());
        writeReview(giheung, application(other, giheung, ApplicationStatus.CONFIRMED), 3);

        mockMvc.perform(get("/api/v1/events/summary").header("Authorization", "Bearer " + host.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(2));

        mockMvc.perform(get("/api/v1/events").param("status", "ENDED").header("Authorization", "Bearer " + host.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.eventId == %d)].reviewCount".formatted(past.getId())).value(2))
                .andExpect(jsonPath("$.data.content[?(@.eventId == %d)].averageRating".formatted(past.getId())).value(4.5))
                .andExpect(jsonPath("$.data.content[?(@.eventId == %d)].reviewCount".formatted(noReview.getId())).value(0))
                .andExpect(jsonPath("$.data.content[?(@.eventId == %d)].averageRating".formatted(noReview.getId()))
                        .value(contains(nullValue())));
    }
}
