package dev.hidly.nubi.review;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramCondition;
import dev.hidly.nubi.program.data.enums.ConditionSource;
import dev.hidly.nubi.program.data.enums.ConditionStatus;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.repository.ProgramConditionRepository;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private ProgramConditionRepository programConditionRepository;
    @Autowired
    private SupportTagRepository supportTagRepository;

    private record Login(String accessToken, Long organizationId) {
    }

    private Login signUp() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgName":"용인 주간활동센터","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                                """.formatted("test-" + UUID.randomUUID() + "@nubi.dev")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Login(JsonPath.read(response, "$.data.accessToken"),
                ((Number) JsonPath.read(response, "$.data.organizationId")).longValue());
    }

    // 행사 등록 API는 지난 날짜를 막아서, 이미 시작한 행사와 확정된 신청은 저장소로 바로 만든다.
    private Application application(Login host, Login applicant, LocalDateTime startAt, ApplicationStatus status) {
        Program program = programRepository.save(Program.builder()
                .hostOrganization(organizationRepository.getReferenceById(host.organizationId()))
                .title("가을 사진 산책").status(ProgramStatus.CLOSED).startAt(startAt).build());
        return applicationRepository.save(Application.builder()
                .program(program)
                .applicantOrganization(organizationRepository.getReferenceById(applicant.organizationId()))
                .participantCount(6).companionCount(2).status(status).build());
    }

    private Map<String, Long> tagIds() {
        return supportTagRepository.findAll().stream().collect(Collectors.toMap(SupportTag::getCode, SupportTag::getId));
    }

    private ResultActions write(Login login, long applicationId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/applications/{applicationId}/review", applicationId)
                .header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 후기를_쓰면_다녀왔어요가_되고_확인_항목이_프로그램_조건에_남는다() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        Application application = application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        Map<String, Long> tags = tagIds();
        String body = """
                {"rating":4,"checks":[
                  {"supportTagId":%d,"answer":"YES"},
                  {"supportTagId":%d,"answer":"NO"},
                  {"supportTagId":%d,"answer":"UNKNOWN"}],
                 "mismatchNote":"해설사가 빠르게 말해서 따라가기 어려웠어요.",
                 "reactions":["ENJOYED","WANT_AGAIN"],
                 "content":"산책로가 평탄해서 편했어요."}
                """.formatted(tags.get("WHEELCHAIR"), tags.get("EASY_WORDS"), tags.get("ACCESSIBLE_TOILET"));

        write(applicant, application.getId(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.checks.length()").value(3))
                .andExpect(jsonPath("$.data.reactions.length()").value(2))
                .andExpect(jsonPath("$.data.isPublic").value(true));

        assertThat(applicationRepository.findById(application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.ATTENDED);
        Map<Long, ConditionStatus> conditions = programConditionRepository
                .findAllByProgramIdAndSource(application.getProgram().getId(), ConditionSource.REVIEW).stream()
                .collect(Collectors.toMap(c -> c.getSupportTag().getId(), ProgramCondition::getStatus));
        assertThat(conditions).containsOnly(
                Map.entry(tags.get("WHEELCHAIR"), ConditionStatus.PROVIDED),
                Map.entry(tags.get("EASY_WORDS"), ConditionStatus.NOT_PROVIDED));

        write(applicant, application.getId(), """
                {"rating":5}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_DUPLICATED"));
    }

    @Test
    void 시작_전_행사나_확정_전_신청은_400_다른_기관은_403() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        String body = """
                {"rating":3}
                """;

        Application notStarted = application(host, applicant, LocalDateTime.now().plusDays(1), ApplicationStatus.CONFIRMED);
        write(applicant, notStarted.getId(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REVIEW_NOT_ALLOWED"));

        Application waiting = application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.WAITING);
        write(applicant, waiting.getId(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REVIEW_NOT_ALLOWED"));

        Application confirmed = application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        write(host, confirmed.getId(), body).andExpect(status().isForbidden());
        write(applicant, confirmed.getId(), """
                {"rating":6}
                """).andExpect(status().isBadRequest());
    }
}
