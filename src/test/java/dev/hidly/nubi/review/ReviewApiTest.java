package dev.hidly.nubi.review;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.common.ai.OpenAiClient;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "file.upload-dir=build/test-uploads")
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
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    // 테스트가 실제 OpenAI를 부르지 않게 한다. 기본은 꺼짐 → 집계로 만든 요약.
    @MockitoBean
    private OpenAiClient openAiClient;

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

    private long review(Login applicant, Application application) throws Exception {
        String response = write(applicant, application.getId(), """
                {"rating":5}
                """).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.reviewId")).longValue();
    }

    private ResultActions upload(Login login, long reviewId, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/reviews/{reviewId}/images", reviewId);
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        return mockMvc.perform(request.header("Authorization", "Bearer " + login.accessToken()));
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("images", "walk.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    void 후기_사진은_쓴_기관만_10장까지_올린다() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        long reviewId = review(applicant, application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED));

        upload(applicant, reviewId, png(), png())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1));
        upload(applicant, reviewId, png(), png(), png(), png(), png(), png(), png(), png())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10));

        upload(applicant, reviewId, png())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REVIEW_IMAGE_COUNT_EXCEEDED"));
        upload(host, reviewId, png()).andExpect(status().isForbidden());
        upload(applicant, 999_999_999L, png())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REVIEW_NOT_FOUND"));
    }

    @Test
    void 이미지가_아닌_파일은_400() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        long reviewId = review(applicant, application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED));

        upload(applicant, reviewId, new MockMultipartFile("images", "a.gif", "image/gif", new byte[]{1}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IMAGE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void 후기를_고치면_통째로_바뀌고_바뀐_답이_프로그램_조건에_다시_반영된다() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        Application application = application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        Map<String, Long> tags = tagIds();
        String written = write(applicant, application.getId(), """
                {"rating":4,"checks":[{"supportTagId":%d,"answer":"YES"},{"supportTagId":%d,"answer":"NO"}],
                 "reactions":["ENJOYED"],"content":"좋았어요"}
                """.formatted(tags.get("WHEELCHAIR"), tags.get("EASY_WORDS")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long reviewId = ((Number) JsonPath.read(written, "$.data.reviewId")).longValue();

        String body = """
                {"rating":2,"checks":[{"supportTagId":%d,"answer":"NO"},{"supportTagId":%d,"answer":"YES"}],
                 "reactions":["HAD_DIFFICULTY"],"isPublic":false}
                """.formatted(tags.get("WHEELCHAIR"), tags.get("ACCESSIBLE_TOILET"));
        mockMvc.perform(put("/api/v1/reviews/{reviewId}", reviewId)
                        .header("Authorization", "Bearer " + applicant.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(2))
                .andExpect(jsonPath("$.data.checks.length()").value(2))
                .andExpect(jsonPath("$.data.checks[?(@.code=='WHEELCHAIR')].answer").value("NO"))
                .andExpect(jsonPath("$.data.checks[?(@.code=='ACCESSIBLE_TOILET')].answer").value("YES"))
                .andExpect(jsonPath("$.data.reactions[0]").value("HAD_DIFFICULTY"))
                .andExpect(jsonPath("$.data.reactions.length()").value(1))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.isPublic").value(false));

        Map<Long, ConditionStatus> conditions = programConditionRepository
                .findAllByProgramIdAndSource(application.getProgram().getId(), ConditionSource.REVIEW).stream()
                .collect(Collectors.toMap(c -> c.getSupportTag().getId(), ProgramCondition::getStatus));
        assertThat(conditions).containsOnly(
                Map.entry(tags.get("WHEELCHAIR"), ConditionStatus.NOT_PROVIDED),
                Map.entry(tags.get("EASY_WORDS"), ConditionStatus.NOT_PROVIDED),
                Map.entry(tags.get("ACCESSIBLE_TOILET"), ConditionStatus.PROVIDED));

        mockMvc.perform(put("/api/v1/reviews/{reviewId}", reviewId)
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/reviews/{reviewId}", 999_999_999L)
                        .header("Authorization", "Bearer " + applicant.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void 같은_신청에_동시에_후기를_보내도_하나만_저장되고_나머지는_409() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        Application application = application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        long wheelchair = tagIds().get("WHEELCHAIR");
        String body = """
                {"rating":5,"checks":[{"supportTagId":%d,"answer":"YES"}]}
                """.formatted(wheelchair);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return write(applicant, application.getId(), body).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(programConditionRepository
                .findAllByProgramIdAndSource(application.getProgram().getId(), ConditionSource.REVIEW)).hasSize(1);
    }

    @Test
    void 삭제된_행사에는_후기를_쓰거나_고칠_수_없다() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        Application written = application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        long reviewId = review(applicant, written);
        Application notWritten = application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        jdbcTemplate.update("update program set deleted_at = now() where id in (?, ?)",
                written.getProgram().getId(), notWritten.getProgram().getId());

        write(applicant, notWritten.getId(), """
                {"rating":3}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REVIEW_NOT_ALLOWED"));
        mockMvc.perform(put("/api/v1/reviews/{reviewId}", reviewId)
                        .header("Authorization", "Bearer " + applicant.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REVIEW_NOT_ALLOWED"));
    }

    @Test
    void 프로그램_후기_목록은_공개_후기만_사진과_함께_최근_순으로_보여_준다() throws Exception {
        Login host = signUp();
        Login first = signUp();
        Login second = signUp();
        Application publicOne = application(host, first, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        Application privateOne = applicationRepository.save(Application.builder()
                .program(publicOne.getProgram())
                .applicantOrganization(organizationRepository.getReferenceById(second.organizationId()))
                .participantCount(3).companionCount(1).status(ApplicationStatus.CONFIRMED).build());
        long wheelchair = tagIds().get("WHEELCHAIR");

        long reviewId = ((Number) JsonPath.read(write(first, publicOne.getId(), """
                {"rating":4,"checks":[{"supportTagId":%d,"answer":"YES"}],"reactions":["ENJOYED"],"content":"편했어요"}
                """.formatted(wheelchair)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.data.reviewId")).longValue();
        upload(first, reviewId, png()).andExpect(status().isOk());
        write(second, privateOne.getId(), """
                {"rating":2,"isPublic":false}
                """).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/programs/{programId}/reviews", publicOne.getProgram().getId())
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].reviewId").value(reviewId))
                .andExpect(jsonPath("$.data.content[0].organizationName").value("용인 주간활동센터"))
                .andExpect(jsonPath("$.data.content[0].rating").value(4))
                .andExpect(jsonPath("$.data.content[0].participantCount").value(6))
                .andExpect(jsonPath("$.data.content[0].companionCount").value(2))
                .andExpect(jsonPath("$.data.content[0].checks[0].code").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.data.content[0].checks[0].answer").value("YES"))
                .andExpect(jsonPath("$.data.content[0].reactions[0]").value("ENJOYED"))
                .andExpect(jsonPath("$.data.content[0].imageUrls.length()").value(1));

        mockMvc.perform(get("/api/v1/programs/{programId}/reviews", publicOne.getProgram().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/programs/{programId}/reviews", 999_999_999L)
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"));
    }

    @Test
    void 받은_후기는_주최_기관만_비공개까지_집계와_함께_본다() throws Exception {
        Login host = signUp();
        Login first = signUp();
        Login second = signUp();
        Application firstApplication = application(host, first, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        long eventId = firstApplication.getProgram().getId();
        Application secondApplication = applicationRepository.save(Application.builder()
                .program(firstApplication.getProgram())
                .applicantOrganization(organizationRepository.getReferenceById(second.organizationId()))
                .participantCount(3).companionCount(1).status(ApplicationStatus.CONFIRMED).build());
        Map<String, Long> tags = tagIds();

        long firstReview = ((Number) JsonPath.read(write(first, firstApplication.getId(), """
                {"rating":5,"checks":[{"supportTagId":%d,"answer":"YES"},{"supportTagId":%d,"answer":"NO"}],
                 "reactions":["ENJOYED","WANT_AGAIN"]}
                """.formatted(tags.get("WHEELCHAIR"), tags.get("EASY_WORDS")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.reviewId")).longValue();
        upload(first, firstReview, png()).andExpect(status().isOk());
        write(second, secondApplication.getId(), """
                {"rating":2,"isPublic":false,"checks":[{"supportTagId":%d,"answer":"YES"}],"reactions":["ENJOYED"]}
                """.formatted(tags.get("WHEELCHAIR"))).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/events/{eventId}/reviews", eventId).header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.organizationCount").value(2))
                .andExpect(jsonPath("$.data.stats.participantCount").value(9))
                .andExpect(jsonPath("$.data.stats.companionCount").value(3))
                .andExpect(jsonPath("$.data.stats.reviewCount").value(2))
                .andExpect(jsonPath("$.data.stats.averageRating").value(3.5))
                .andExpect(jsonPath("$.data.checks[?(@.code=='WHEELCHAIR')].yesCount").value(2))
                .andExpect(jsonPath("$.data.checks[?(@.code=='EASY_WORDS')].noCount").value(1))
                .andExpect(jsonPath("$.data.reactions[?(@.reaction=='ENJOYED')].count").value(2))
                .andExpect(jsonPath("$.data.reactions[?(@.reaction=='WANT_AGAIN')].count").value(1))
                .andExpect(jsonPath("$.data.reviews.totalElements").value(2));

        mockMvc.perform(get("/api/v1/events/{eventId}/reviews", eventId).param("sort", "RATING_ASC")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(jsonPath("$.data.reviews.content[0].rating").value(2))
                .andExpect(jsonPath("$.data.reviews.content[0].isPublic").value(false));
        mockMvc.perform(get("/api/v1/events/{eventId}/reviews", eventId).param("photoOnly", "true")
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(jsonPath("$.data.reviews.totalElements").value(1))
                .andExpect(jsonPath("$.data.reviews.content[0].reviewId").value(firstReview));

        mockMvc.perform(get("/api/v1/events/{eventId}/reviews", eventId).header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_ACCESS_DENIED"));
    }

    private ResultActions replyTo(Login login, long reviewId, String content) throws Exception {
        return mockMvc.perform(post("/api/v1/reviews/{reviewId}/reply", reviewId)
                .header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"content":"%s"}
                        """.formatted(content)));
    }

    @Test
    void 주최_기관이_답글을_달면_두_후기_목록에_보이고_답글_기다림이_줄어든다() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        Application application = application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        long eventId = application.getProgram().getId();
        long reviewId = review(applicant, application);

        mockMvc.perform(get("/api/v1/events/{eventId}/reviews", eventId).header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(jsonPath("$.data.stats.awaitingReplyCount").value(1))
                .andExpect(jsonPath("$.data.reviews.content[0].reply").doesNotExist());

        replyTo(applicant, reviewId, "제가 쓴 후기에 답글").andExpect(status().isForbidden());
        replyTo(host, reviewId, "  다음 행사부터 그늘막을 두 개 더 둘게요.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewId").value(reviewId))
                .andExpect(jsonPath("$.data.content").value("다음 행사부터 그늘막을 두 개 더 둘게요."));
        replyTo(host, reviewId, "한 번 더")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_REPLY_DUPLICATED"));

        mockMvc.perform(get("/api/v1/events/{eventId}/reviews", eventId).header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(jsonPath("$.data.stats.awaitingReplyCount").value(0))
                .andExpect(jsonPath("$.data.reviews.content[0].reply.content").value("다음 행사부터 그늘막을 두 개 더 둘게요."));
        mockMvc.perform(get("/api/v1/programs/{programId}/reviews", eventId).header("Authorization", "Bearer " + applicant.accessToken()))
                .andExpect(jsonPath("$.data.content[0].reply.organizationName").value("용인 주간활동센터"));
    }

    @Test
    void 빈_답글이나_없는_후기는_400_404() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        long reviewId = review(applicant, application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED));

        replyTo(host, reviewId, "   ").andExpect(status().isBadRequest());
        replyTo(host, 999_999_999L, "답글")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REVIEW_NOT_FOUND"));
    }

    // 주최가 휠체어를 제공한다고 체크한 행사에 후기 두 개: 둘 다 휠체어 "아니었어요", 하나는 쉬는 시간 부족
    private long eventWithTwoReviews(Login host) throws Exception {
        Login first = signUp();
        Login second = signUp();
        Application firstApplication = application(host, first, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        Map<String, Long> tags = tagIds();
        programConditionRepository.save(ProgramCondition.builder().program(firstApplication.getProgram())
                .supportTag(supportTagRepository.getReferenceById(tags.get("WHEELCHAIR")))
                .status(ConditionStatus.PROVIDED).source(ConditionSource.HOST).build());
        Application secondApplication = applicationRepository.save(Application.builder()
                .program(firstApplication.getProgram())
                .applicantOrganization(organizationRepository.getReferenceById(second.organizationId()))
                .participantCount(3).companionCount(1).status(ApplicationStatus.CONFIRMED).build());
        write(first, firstApplication.getId(), """
                {"rating":3,"checks":[{"supportTagId":%d,"answer":"NO"}],"reactions":["SHORT_BREAKS"],
                 "mismatchNote":"입구에 턱이 있었어요","content":"햇볕이 강해서 그늘막이 더 있었으면 좋겠어요."}
                """.formatted(tags.get("WHEELCHAIR"))).andExpect(status().isOk());
        write(second, secondApplication.getId(), """
                {"rating":4,"checks":[{"supportTagId":%d,"answer":"NO"}],"content":"순서표가 어려웠어요."}
                """.formatted(tags.get("WHEELCHAIR"))).andExpect(status().isOk());
        return firstApplication.getProgram().getId();
    }

    private ResultActions summary(Login login, long eventId) throws Exception {
        return mockMvc.perform(get("/api/v1/events/{eventId}/reviews/summary", eventId)
                .header("Authorization", "Bearer " + login.accessToken()));
    }

    @Test
    void AI_없이도_집계로_개선점을_만들고_공고와_엇갈린_지원_항목을_알려준다() throws Exception {
        Login host = signUp();
        long eventId = eventWithTwoReviews(host);

        summary(host, eventId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(2))
                .andExpect(jsonPath("$.data.aiWritten").value(false))
                .andExpect(jsonPath("$.data.summary").value("후기 2개, 평균 별점 3.5점이에요."))
                .andExpect(jsonPath("$.data.improvements[0]").value("휠체어 다시 확인하기"))
                .andExpect(jsonPath("$.data.improvements").value(hasItem("쉬는 시간 늘리기")))
                .andExpect(jsonPath("$.data.conditionAlerts.length()").value(1))
                .andExpect(jsonPath("$.data.conditionAlerts[0].code").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.data.conditionAlerts[0].noCount").value(2));
    }

    @Test
    void AI가_쓴_요약을_쓰고_규칙을_어긴_개선점은_집계로_바꾼다() throws Exception {
        Login host = signUp();
        long eventId = eventWithTwoReviews(host);
        given(openAiClient.isEnabled()).willReturn(true);
        given(openAiClient.completeJson(anyString(), anyString())).willReturn(Optional.of(objectMapper.readTree("""
                {"summary":"전반적으로 만족했지만 입구 턱과 햇볕이 아쉬웠어요.","improvements":["그늘막 늘리기","순서표를 그림으로 바꾸기"]}
                """)));
        summary(host, eventId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiWritten").value(true))
                .andExpect(jsonPath("$.data.summary").value("전반적으로 만족했지만 입구 턱과 햇볕이 아쉬웠어요."))
                .andExpect(jsonPath("$.data.improvements[0]").value("그늘막 늘리기"))
                .andExpect(jsonPath("$.data.conditionAlerts.length()").value(1));

        given(openAiClient.completeJson(anyString(), anyString())).willReturn(Optional.of(objectMapper.readTree("""
                {"summary":"좋았어요.","improvements":["%s"]}
                """.formatted("아주 긴 개선점 ".repeat(10)))));
        summary(host, eventId)
                .andExpect(jsonPath("$.data.summary").value("좋았어요."))
                .andExpect(jsonPath("$.data.improvements[0]").value("휠체어 다시 확인하기"));
    }

    @Test
    void 후기가_없으면_AI를_부르지_않고_주최가_아니면_403() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        Application application = application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);
        given(openAiClient.isEnabled()).willReturn(true);

        summary(host, application.getProgram().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.improvements.length()").value(0));
        verify(openAiClient, never()).completeJson(anyString(), anyString());

        summary(applicant, application.getProgram().getId()).andExpect(status().isForbidden());
    }

    @Test
    void 받은_후기가_없으면_평균_별점은_null() throws Exception {
        Login host = signUp();
        Login applicant = signUp();
        Application application = application(host, applicant, LocalDateTime.now().minusDays(1), ApplicationStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/events/{eventId}/reviews", application.getProgram().getId())
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.reviewCount").value(0))
                .andExpect(jsonPath("$.data.stats.averageRating").doesNotExist())
                .andExpect(jsonPath("$.data.checks.length()").value(0))
                .andExpect(jsonPath("$.data.reviews.totalElements").value(0));
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
