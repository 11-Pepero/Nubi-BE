package dev.hidly.nubi.document;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.common.ai.OpenAiClient;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationDocumentApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SupportTagRepository supportTagRepository;
    @Autowired
    private ObjectMapper objectMapper;
    // 테스트가 실제 OpenAI를 부르지 않게 한다(local-secret.properties에 키가 있어도). 기본은 꺼짐 → 초안 사용.
    @MockitoBean
    private OpenAiClient openAiClient;

    private record Login(String token) {
    }

    private Login signUp(String orgName) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgName":"%s","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                                """.formatted(orgName, "test-" + UUID.randomUUID() + "@nubi.dev")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Login(JsonPath.read(response, "$.data.accessToken"));
    }

    private long id(ResultActions actions, String path) throws Exception {
        return ((Number) JsonPath.read(actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private Map<String, Long> tags() {
        return supportTagRepository.findAll().stream().collect(Collectors.toMap(SupportTag::getCode, SupportTag::getId));
    }

    private ResultActions write(Login login, long applicationId) throws Exception {
        return mockMvc.perform(post("/api/v1/applications/{applicationId}/documents", applicationId)
                .header("Authorization", "Bearer " + login.token()));
    }

    // 주최: 경사로(휠체어)는 공고에 있다고 체크한 행사. 신청: 휠체어 3명 + 쉬운 말 안내 + 개별 케어 한 명.
    private long applicationWithWheelchairOnNotice(Login host, Login applicant) throws Exception {
        Map<String, Long> tags = tags();
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0);
        long eventId = id(mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + host.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"그림책 낭독회","startAt":"%s","status":"OPEN","conditions":[{"supportTagId":%d}]}
                        """.formatted(start, tags.get("WHEELCHAIR")))), "$.data.programId");
        long groupId = id(mockMvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + applicant.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"주간활동 A","memberCount":8,"supports":[{"supportTagId":%d,"quantity":3},{"supportTagId":%d}]}
                        """.formatted(tags.get("WHEELCHAIR"), tags.get("EASY_WORDS")))), "$.data.id");
        long careId = id(mockMvc.perform(post("/api/v1/care-persons").header("Authorization", "Bearer " + applicant.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"alias":"햇살","items":[{"content":"큰 소리에 놀라서 조용한 자리가 필요해요"}]}
                        """)), "$.data.id");
        return id(mockMvc.perform(post("/api/v1/programs/{programId}/applications", eventId)
                .header("Authorization", "Bearer " + applicant.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"groupId":%d,"careIds":[%d],"companionCount":4}
                        """.formatted(groupId, careId))), "$.data.applicationId");
    }

    @Test
    void 공고에_있는_지원은_묻지_않고_확인이_필요한_항목만_문의_문자와_통화_대본에_담는다() throws Exception {
        Login host = signUp("용인시 문화재단");
        Login applicant = signUp("용인 주간보호센터");
        long applicationId = applicationWithWheelchairOnNotice(host, applicant);

        write(applicant, applicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmed[0]").value("휠체어 3명"))
                .andExpect(jsonPath("$.data.toAsk[0]").value("쉬운 말 안내"))
                .andExpect(jsonPath("$.data.documents.length()").value(3))
                .andExpect(jsonPath("$.data.documents[?(@.type=='FORM')].content").value(hasItem(containsString("신청 기관: 용인 주간보호센터"))))
                .andExpect(jsonPath("$.data.documents[?(@.type=='FORM')].content").value(hasItem(containsString("이용자 8명, 동반 인력 4명 (총 12명)"))))
                .andExpect(jsonPath("$.data.documents[?(@.type=='FORM')].content").value(hasItem(containsString("큰 소리에 놀라서 조용한 자리가 필요해요"))))
                .andExpect(jsonPath("$.data.documents[?(@.type=='INQUIRY')].content").value(hasItem(containsString("쉬운 말 안내이(가) 가능한지"))))
                .andExpect(jsonPath("$.data.documents[?(@.type=='INQUIRY')].content").value(everyItem(not(containsString("휠체어")))))
                .andExpect(jsonPath("$.data.documents[?(@.type=='CALL_SCRIPT')].content").value(hasItem(containsString("12명 신청하려고 해요"))));
    }

    @Test
    void 다시_쓰면_서류가_늘지_않고_같은_서류를_고친다() throws Exception {
        Login host = signUp("용인시 문화재단");
        Login applicant = signUp("용인 주간보호센터");
        long applicationId = applicationWithWheelchairOnNotice(host, applicant);

        String first = write(applicant, applicationId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = write(applicant, applicationId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Object firstIds = JsonPath.read(first, "$.data.documents[*].documentId");
        Object secondIds = JsonPath.read(second, "$.data.documents[*].documentId");
        assertThat(secondIds).isEqualTo(firstIds);
    }

    @Test
    void 신청서를_한글이_들어간_PDF로_내려받는다() throws Exception {
        Login host = signUp("용인시 문화재단");
        Login applicant = signUp("용인 주간보호센터");
        long applicationId = applicationWithWheelchairOnNotice(host, applicant);
        String written = write(applicant, applicationId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> formIds = JsonPath.read(written, "$.data.documents[?(@.type=='FORM')].documentId");
        long formId = formIds.getFirst().longValue();

        MvcResult result = mockMvc.perform(get("/api/v1/applications/{applicationId}/documents/{docId}/pdf", applicationId, formId)
                        .header("Authorization", "Bearer " + applicant.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".pdf")))
                .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("프로그램 참가 신청서").contains("용인 주간보호센터");

        mockMvc.perform(get("/api/v1/applications/{applicationId}/documents/{docId}/pdf", applicationId, formId)
                        .header("Authorization", "Bearer " + host.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/applications/{applicationId}/documents/{docId}/pdf", applicationId, 999_999_999L)
                        .header("Authorization", "Bearer " + applicant.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void AI가_다듬은_서류를_쓰고_규칙에_안_맞는_서류는_초안을_쓴다() throws Exception {
        Login host = signUp("용인시 문화재단");
        Login applicant = signUp("용인 주간보호센터");
        long applicationId = applicationWithWheelchairOnNotice(host, applicant);
        given(openAiClient.isEnabled()).willReturn(true);
        // 신청서는 제목 줄이 빠져서 쓰지 않고, 문의 문자·통화 대본은 AI 글을 쓴다.
        given(openAiClient.completeJson(anyString(), anyString())).willReturn(Optional.of(objectMapper.readTree("""
                {"form":"제목 없는 신청서","inquiry":"안녕하세요, 쉬운 말 안내가 가능한지 여쭤봐요.","callScript":"\\"쉬운 말 안내 가능할까요?\\""}
                """)));

        write(applicant, applicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiWritten").value(true))
                .andExpect(jsonPath("$.data.documents[?(@.type=='INQUIRY')].content").value(hasItem("안녕하세요, 쉬운 말 안내가 가능한지 여쭤봐요.")))
                .andExpect(jsonPath("$.data.documents[?(@.type=='FORM')].content").value(hasItem(containsString("신청 기관: 용인 주간보호센터"))));
    }

    @Test
    void AI_호출이_실패하면_초안으로_쓴다() throws Exception {
        Login host = signUp("용인시 문화재단");
        Login applicant = signUp("용인 주간보호센터");
        long applicationId = applicationWithWheelchairOnNotice(host, applicant);
        given(openAiClient.isEnabled()).willReturn(true);
        given(openAiClient.completeJson(anyString(), anyString())).willReturn(Optional.empty());

        write(applicant, applicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiWritten").value(false))
                .andExpect(jsonPath("$.data.documents[?(@.type=='INQUIRY')].content").value(hasItem(containsString("쉬운 말 안내이(가) 가능한지"))));
    }

    @Test
    void 다른_기관은_403_취소된_신청은_400() throws Exception {
        Login host = signUp("용인시 문화재단");
        Login applicant = signUp("용인 주간보호센터");
        long applicationId = applicationWithWheelchairOnNotice(host, applicant);

        write(host, applicationId).andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/applications/{applicationId}/cancel", applicationId)
                        .header("Authorization", "Bearer " + applicant.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        write(applicant, applicationId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));
    }
}
