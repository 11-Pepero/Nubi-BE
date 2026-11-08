package dev.hidly.nubi.program;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Eligibility;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramCondition;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.entity.VenueFacility;
import dev.hidly.nubi.program.data.enums.*;
import dev.hidly.nubi.program.data.repository.*;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EligibilityApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramConditionRepository programConditionRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private VenueFacilityRepository venueFacilityRepository;
    @Autowired
    private EligibilityRepository eligibilityRepository;
    @Autowired
    private SupportTagRepository supportTagRepository;

    private record Login(String accessToken, Long organizationId) {
    }

    private Login login;
    private YearMonth month;
    private SupportTag wheelchair;
    private SupportTag easyWords;
    private SupportTag powerWheelchair;

    @BeforeEach
    void setUp() throws Exception {
        login = signUp();
        month = YearMonth.of(ThreadLocalRandom.current().nextInt(2100, 9000), ThreadLocalRandom.current().nextInt(1, 13));
        wheelchair = tag("WHEELCHAIR");
        easyWords = tag("EASY_WORDS");
        powerWheelchair = tag("POWER_WHEELCHAIR");
    }

    private SupportTag tag(String code) {
        return supportTagRepository.findAll().stream().filter(tag -> tag.getCode().equals(code)).findFirst().orElseThrow();
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

    private long createGroup(String token, int memberCount, String supportsJson) throws Exception {
        String response = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"주간활동 A","memberCount":%d,"supports":%s}
                                """.formatted(memberCount, supportsJson)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private long wheelchairAndEasyWordsGroup() throws Exception {
        return createGroup(login.accessToken(), 8, """
                [{"supportTagId":%d,"quantity":3},{"supportTagId":%d}]
                """.formatted(wheelchair.getId(), easyWords.getId()));
    }

    private long createCarePerson(Long groupId, boolean applyToJudgement, String itemsJson) throws Exception {
        String response = mockMvc.perform(post("/api/v1/care-persons")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"alias":"민들레","groupId":%d,"applyToJudgement":%s,"items":%s}
                                """.formatted(groupId, applyToJudgement, itemsJson)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private Program createProgram(String title, Venue venue, Integer capacity) {
        LocalDateTime startAt = month.atDay(10).atTime(10, 0);
        return programRepository.save(Program.builder()
                .title(title)
                .hostName("용인시 장애인복지관")
                .venue(venue)
                .capacity(capacity)
                .startAt(startAt)
                .endAt(startAt.plusHours(2))
                .status(ProgramStatus.OPEN)
                .build());
    }

    private Program createProgram(String title) {
        return createProgram(title, null, null);
    }

    private void condition(Program program, SupportTag tag, ConditionStatus status, ConditionSource source, String confidence) {
        programConditionRepository.save(ProgramCondition.builder()
                .program(program)
                .supportTag(tag)
                .status(status)
                .source(source)
                .confidence(confidence == null ? null : new BigDecimal(confidence))
                .evidence(tag.getLabel() + " 근거 문장")
                .build());
    }

    private void provideAll(Program program) {
        condition(program, wheelchair, ConditionStatus.PROVIDED, ConditionSource.HOST, null);
        condition(program, easyWords, ConditionStatus.PROVIDED, ConditionSource.HOST, null);
    }

    private org.springframework.test.web.servlet.ResultActions eligibility(Long programId, Long groupId) throws Exception {
        return mockMvc.perform(get("/api/v1/programs/{programId}/eligibility", programId)
                .param("groupId", String.valueOf(groupId))
                .header("Authorization", "Bearer " + login.accessToken()));
    }

    @Test
    void 필요한_지원을_모두_제공하면_참여_가능() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        Program program = createProgram("모두 제공");
        provideAll(program);

        eligibility(program.getId(), groupId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.reasons.capacityExceeded").value(false))
                .andExpect(jsonPath("$.data.reasons.items.length()").value(2))
                .andExpect(jsonPath("$.data.reasons.items[0].code").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.data.reasons.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.reasons.items[0].result").value("MET"))
                .andExpect(jsonPath("$.data.reasons.items[0].basis").value("HOST"))
                .andExpect(jsonPath("$.data.reasons.items[0].evidence").value("휠체어 근거 문장"));
    }

    @Test
    void 필수_분류_지원이_없으면_어려움_필수가_아니면_확인_필요() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        Program noWheelchair = createProgram("휠체어 불가");
        condition(noWheelchair, wheelchair, ConditionStatus.NOT_PROVIDED, ConditionSource.HOST, null);
        condition(noWheelchair, easyWords, ConditionStatus.PROVIDED, ConditionSource.HOST, null);
        Program noEasyWords = createProgram("쉬운 말 없음");
        condition(noEasyWords, wheelchair, ConditionStatus.PROVIDED, ConditionSource.HOST, null);
        condition(noEasyWords, easyWords, ConditionStatus.NOT_PROVIDED, ConditionSource.HOST, null);

        eligibility(noWheelchair.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("NOT"))
                .andExpect(jsonPath("$.data.reasons.items[0].result").value("MISSING"));
        eligibility(noEasyWords.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("CHECK"))
                .andExpect(jsonPath("$.data.reasons.items[1].result").value("MISSING"));
    }

    @Test
    void 정보가_없으면_확인_필요이고_장소_편의시설로_휠체어를_판단한다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        Program unknown = createProgram("정보 없음");

        Venue venue = venueRepository.save(Venue.builder().name("엘리베이터 있는 곳").district(District.SUJI).build());
        venueFacilityRepository.save(VenueFacility.builder().venue(venue).facilityType(FacilityType.ELEVATOR)
                .status(FacilityStatus.YES).source(FacilitySource.PUBLIC_DATA).build());
        Program withElevator = createProgram("엘리베이터", venue, null);
        condition(withElevator, easyWords, ConditionStatus.PROVIDED, ConditionSource.HOST, null);

        eligibility(unknown.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("CHECK"))
                .andExpect(jsonPath("$.data.reasons.items[0].result").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.reasons.items[0].basis").isEmpty());
        eligibility(withElevator.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.reasons.items[0].result").value("MET"))
                .andExpect(jsonPath("$.data.reasons.items[0].basis").value("VENUE_FACILITY"));
    }

    @Test
    void AI_조건은_확신도_0_8_이상만_믿는다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        Program lowConfidence = createProgram("AI 낮음");
        condition(lowConfidence, wheelchair, ConditionStatus.PROVIDED, ConditionSource.AI, "0.60");
        condition(lowConfidence, easyWords, ConditionStatus.PROVIDED, ConditionSource.HOST, null);
        Program highConfidence = createProgram("AI 높음");
        condition(highConfidence, wheelchair, ConditionStatus.PROVIDED, ConditionSource.AI, "0.90");
        condition(highConfidence, easyWords, ConditionStatus.PROVIDED, ConditionSource.HOST, null);

        eligibility(lowConfidence.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("CHECK"))
                .andExpect(jsonPath("$.data.reasons.items[0].result").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.reasons.items[0].basis").value("AI"))
                .andExpect(jsonPath("$.data.reasons.items[0].confidence").value(0.60));
        eligibility(highConfidence.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.reasons.items[0].basis").value("AI"));
    }

    @Test
    void 출처가_여러_개면_주최_후기_AI_순으로_믿는다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        Program program = createProgram("출처 충돌");
        condition(program, wheelchair, ConditionStatus.PROVIDED, ConditionSource.AI, "0.95");
        condition(program, wheelchair, ConditionStatus.NOT_PROVIDED, ConditionSource.HOST, null);
        condition(program, easyWords, ConditionStatus.UNKNOWN, ConditionSource.HOST, null);
        condition(program, easyWords, ConditionStatus.PROVIDED, ConditionSource.REVIEW, null);

        eligibility(program.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("NOT"))
                .andExpect(jsonPath("$.data.reasons.items[0].result").value("MISSING"))
                .andExpect(jsonPath("$.data.reasons.items[0].basis").value("HOST"))
                .andExpect(jsonPath("$.data.reasons.items[1].result").value("MET"))
                .andExpect(jsonPath("$.data.reasons.items[1].basis").value("REVIEW"));
    }

    @Test
    void 그룹_인원이_정원보다_많으면_어려움() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        Program program = createProgram("정원 5명", null, 5);
        provideAll(program);

        eligibility(program.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("NOT"))
                .andExpect(jsonPath("$.data.reasons.capacityExceeded").value(true));
    }

    @Test
    void 판정_반영_개별_케어는_최대_확인_필요까지만_영향을_준다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        Program program = createProgram("개별 케어");
        provideAll(program);
        condition(program, powerWheelchair, ConditionStatus.NOT_PROVIDED, ConditionSource.HOST, null);

        createCarePerson(groupId, false, """
                [{"supportTagId":%d}]
                """.formatted(powerWheelchair.getId()));
        eligibility(program.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.reasons.careChecks.length()").value(0));

        createCarePerson(groupId, true, """
                [{"content":"점심 후 30분 휴식"}]
                """);
        eligibility(program.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.reasons.careChecks[0].result").value("NOTE"));

        createCarePerson(groupId, true, """
                [{"supportTagId":%d,"content":"전동휠체어 충전 콘센트"}]
                """.formatted(powerWheelchair.getId()));
        eligibility(program.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("CHECK"))
                .andExpect(jsonPath("$.data.reasons.careChecks.length()").value(2))
                .andExpect(jsonPath("$.data.reasons.careChecks[1].alias").value("민들레"))
                .andExpect(jsonPath("$.data.reasons.careChecks[1].code").value("POWER_WHEELCHAIR"))
                .andExpect(jsonPath("$.data.reasons.careChecks[1].result").value("MISSING"));
    }

    @Test
    void 참여_가능_프로그램은_결과순으로_나오고_결과로_거를_수_있다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        Program check = createProgram("확인 필요");
        Program not = createProgram("어려움");
        condition(not, wheelchair, ConditionStatus.NOT_PROVIDED, ConditionSource.HOST, null);
        Program eligible = createProgram("참여 가능");
        provideAll(eligible);

        mockMvc.perform(get("/api/v1/programs/eligible")
                        .param("groupId", String.valueOf(groupId))
                        .param("month", month.toString())
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].program.programId").value(eligible.getId()))
                .andExpect(jsonPath("$.data.content[0].result").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.content[0].metCount").value(2))
                .andExpect(jsonPath("$.data.content[1].program.programId").value(check.getId()))
                .andExpect(jsonPath("$.data.content[1].unknownCount").value(2))
                .andExpect(jsonPath("$.data.content[2].program.programId").value(not.getId()))
                .andExpect(jsonPath("$.data.content[2].missingCount").value(1));

        mockMvc.perform(get("/api/v1/programs/eligible")
                        .param("groupId", String.valueOf(groupId))
                        .param("month", month.toString())
                        .param("result", "CHECK")
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].program.title").value("확인 필요"));
    }

    @Test
    void careIds를_주면_그_개별_케어로_판정한다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        long otherGroupId = createGroup(login.accessToken(), 5, "[]");
        long careId = createCarePerson(otherGroupId, true, """
                [{"supportTagId":%d}]
                """.formatted(powerWheelchair.getId()));
        Program program = createProgram("다른 그룹 케어");
        provideAll(program);

        eligibility(program.getId(), groupId)
                .andExpect(jsonPath("$.data.result").value("ELIGIBLE"));

        mockMvc.perform(get("/api/v1/programs/{programId}/eligibility", program.getId())
                        .param("groupId", String.valueOf(groupId))
                        .param("careIds", String.valueOf(careId))
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(jsonPath("$.data.result").value("CHECK"))
                .andExpect(jsonPath("$.data.reasons.careChecks[0].careId").value(careId));
    }

    @Test
    void 그룹을_만들거나_고치면_판정을_다시_계산해_저장한다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        Program program = createProgram("저장 확인");
        condition(program, wheelchair, ConditionStatus.NOT_PROVIDED, ConditionSource.HOST, null);
        condition(program, easyWords, ConditionStatus.PROVIDED, ConditionSource.HOST, null);

        mockMvc.perform(put("/api/v1/groups/{groupId}", groupId)
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"주간활동 A","memberCount":8,"supports":[{"supportTagId":%d}]}
                                """.formatted(easyWords.getId())))
                .andExpect(status().isOk());

        Eligibility saved = eligibilityRepository.findAllByGroupId(groupId).stream()
                .filter(eligibility -> eligibility.getProgram().getId().equals(program.getId()))
                .findFirst().orElseThrow();
        assertThat(saved.getResult()).isEqualTo(EligibilityResult.ELIGIBLE);
        assertThat(saved.getReasons().items()).hasSize(1);
        assertThat(saved.getReasons().items().getFirst().code()).isEqualTo("EASY_WORDS");
        assertThat(saved.getComputedAt()).isNotNull();
    }

    @Test
    void 그룹_카드에_참여_가능과_확인_필요_수가_나온다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        String before = mockMvc.perform(get("/api/v1/groups/{groupId}", groupId)
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andReturn().getResponse().getContentAsString();
        int eligibleBefore = JsonPath.read(before, "$.data.eligibleCount");
        int checkBefore = JsonPath.read(before, "$.data.checkCount");

        provideAll(createProgram("참여 가능 1"));
        createProgram("확인 필요 1");
        Program not = createProgram("어려움 1");
        condition(not, wheelchair, ConditionStatus.NOT_PROVIDED, ConditionSource.HOST, null);

        mockMvc.perform(get("/api/v1/groups/{groupId}", groupId).header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(jsonPath("$.data.eligibleCount").value(eligibleBefore + 1))
                .andExpect(jsonPath("$.data.checkCount").value(checkBefore + 1));
        mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(jsonPath("$.data[0].eligibleCount").value(eligibleBefore + 1))
                .andExpect(jsonPath("$.data[0].checkCount").value(checkBefore + 1));
    }

    @Test
    void 프로그램_상세에_groupId를_주면_판정이_붙고_로그인이_필요하다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        Program program = createProgram("상세 판정");
        provideAll(program);

        mockMvc.perform(get("/api/v1/programs/{programId}", program.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligibility").isEmpty());

        mockMvc.perform(get("/api/v1/programs/{programId}", program.getId())
                        .param("groupId", String.valueOf(groupId))
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligibility.result").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.eligibility.groupId").value(groupId));

        mockMvc.perform(get("/api/v1/programs/{programId}", program.getId())
                        .param("groupId", String.valueOf(groupId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 다른_기관의_그룹과_개별_케어로는_판정할_수_없다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        long careId = createCarePerson(groupId, true, "[]");
        Program program = createProgram("남의 그룹");
        Login other = signUp();
        long otherGroupId = createGroup(other.accessToken(), 3, "[]");

        mockMvc.perform(get("/api/v1/programs/{programId}/eligibility", program.getId())
                        .param("groupId", String.valueOf(groupId))
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/programs/{programId}/eligibility", program.getId())
                        .param("groupId", String.valueOf(otherGroupId))
                        .param("careIds", String.valueOf(careId))
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/programs/eligible").param("groupId", String.valueOf(groupId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 개별_케어_상세에_이_사람_때문에_판정이_달라지는_프로그램이_나온다() throws Exception {
        SupportTag signLanguage = tag("SIGN_LANGUAGE");
        long groupId = createGroup(login.accessToken(), 6, """
                [{"supportTagId":%d}]
                """.formatted(signLanguage.getId()));
        LocalDateTime earliest = LocalDateTime.of(2000, 1, 1, 0, 0).minusSeconds(System.currentTimeMillis() / 1000);
        Program affected = programRepository.save(Program.builder().title("수어 제공, 충전 불가")
                .startAt(earliest).status(ProgramStatus.OPEN).build());
        condition(affected, signLanguage, ConditionStatus.PROVIDED, ConditionSource.HOST, null);
        condition(affected, powerWheelchair, ConditionStatus.NOT_PROVIDED, ConditionSource.HOST, null);
        Program alreadyCheck = programRepository.save(Program.builder().title("수어 정보 없음")
                .startAt(earliest.plusMinutes(1)).status(ProgramStatus.OPEN).build());
        condition(alreadyCheck, powerWheelchair, ConditionStatus.NOT_PROVIDED, ConditionSource.HOST, null);

        long careId = createCarePerson(groupId, true, """
                [{"supportTagId":%d,"content":"충전 콘센트"},{"content":"점심 후 휴식"}]
                """.formatted(powerWheelchair.getId()));

        String response = mockMvc.perform(get("/api/v1/care-persons/{careId}", careId)
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgementImpact.applied").value(true))
                .andReturn().getResponse().getContentAsString();
        String program = "$.data.judgementImpact.programs[?(@.programId == %d)]";
        java.util.List<String> without = JsonPath.read(response, program.formatted(affected.getId()) + ".resultWithout");
        java.util.List<String> with = JsonPath.read(response, program.formatted(affected.getId()) + ".resultWith");
        java.util.List<String> checkCodes = JsonPath.read(response, program.formatted(affected.getId()) + ".checks[*].code");
        java.util.List<String> checkResults = JsonPath.read(response, program.formatted(affected.getId()) + ".checks[*].result");
        assertThat(without).containsExactly("ELIGIBLE");
        assertThat(with).containsExactly("CHECK");
        assertThat(checkCodes).containsExactly("POWER_WHEELCHAIR");
        assertThat(checkResults).containsExactly("MISSING");
        java.util.List<Integer> ids = JsonPath.read(response, "$.data.judgementImpact.programs[*].programId");
        assertThat(ids).doesNotContain(alreadyCheck.getId().intValue());
    }

    @Test
    void 판정_반영을_끄거나_문장만_있는_개별_케어는_영향이_없다() throws Exception {
        long groupId = wheelchairAndEasyWordsGroup();
        long notApplied = createCarePerson(groupId, false, """
                [{"supportTagId":%d}]
                """.formatted(powerWheelchair.getId()));
        long noteOnly = createCarePerson(groupId, true, """
                [{"content":"점심 후 휴식"}]
                """);

        mockMvc.perform(get("/api/v1/care-persons/{careId}", notApplied)
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(jsonPath("$.data.judgementImpact.applied").value(false))
                .andExpect(jsonPath("$.data.judgementImpact.affectedCount").value(0));
        mockMvc.perform(get("/api/v1/care-persons/{careId}", noteOnly)
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(jsonPath("$.data.judgementImpact.applied").value(true))
                .andExpect(jsonPath("$.data.judgementImpact.affectedCount").value(0));
        mockMvc.perform(get("/api/v1/care-persons").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(jsonPath("$.data[0].judgementImpact").isEmpty());
    }
}
