package dev.hidly.nubi.promotion;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.common.ai.OpenAiClient;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.entity.VenueFacility;
import dev.hidly.nubi.program.data.enums.FacilitySource;
import dev.hidly.nubi.program.data.enums.FacilityStatus;
import dev.hidly.nubi.program.data.enums.FacilityType;
import dev.hidly.nubi.program.data.repository.VenueFacilityRepository;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "file.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
class PromotionApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SupportTagRepository supportTagRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private VenueFacilityRepository venueFacilityRepository;
    @Autowired
    private ObjectMapper objectMapper;
    // 테스트가 실제 OpenAI를 부르지 않게 한다. 기본은 꺼짐 → 초안 사용.
    @MockitoBean
    private OpenAiClient openAiClient;

    private String signUp() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgName":"용인 주간보호센터","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"}
                                """.formatted("test-" + UUID.randomUUID() + "@nubi.dev")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    // 승강기·장애인 화장실이 확인된 장소에서, 주최가 쉬운 말 안내·조용한 휴식 공간을 제공한다고 체크한 축제
    private LocalDateTime start;

    private long festival(String token) throws Exception {
        Map<String, Long> tags = supportTagRepository.findAll().stream()
                .collect(Collectors.toMap(SupportTag::getCode, SupportTag::getId));
        Venue venue = venueRepository.save(Venue.builder().name("센터 강당").district(District.CHEOIN).build());
        venueFacilityRepository.save(VenueFacility.builder().venue(venue).facilityType(FacilityType.ELEVATOR)
                .status(FacilityStatus.YES).source(FacilitySource.PUBLIC_DATA).build());
        venueFacilityRepository.save(VenueFacility.builder().venue(venue).facilityType(FacilityType.ACCESSIBLE_TOILET)
                .status(FacilityStatus.YES).source(FacilitySource.PUBLIC_DATA).build());
        start = LocalDateTime.now().plusDays(10).withHour(13).withMinute(0).withSecond(0).withNano(0);
        String response = mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"함께 걷는 가을 축제","startAt":"%s","endAt":"%s","capacity":40,"venueId":%d,"status":"OPEN",
                                 "description":"가을 산책과 공연을 함께 즐겨요.",
                                 "conditions":[{"supportTagId":%d},{"supportTagId":%d},{"supportTagId":%d,"status":"UNKNOWN"}]}
                                """.formatted(start, start.withHour(16), venue.getId(),
                                tags.get("EASY_WORDS"), tags.get("QUIET_REST_SPACE"), tags.get("SIGN_LANGUAGE"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.programId")).longValue();
    }

    private ResultActions write(String token, long eventId) throws Exception {
        return mockMvc.perform(post("/api/v1/events/{eventId}/promotions", eventId).header("Authorization", "Bearer " + token));
    }

    private String monthDay() {
        return "%d월 %d일".formatted(start.getMonthValue(), start.getDayOfMonth());
    }

    @Test
    void AI_없이도_세_가지_홍보문을_행사_사실만으로_쓴다() throws Exception {
        String token = signUp();
        long eventId = festival(token);

        write(token, eventId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiWritten").value(false))
                .andExpect(jsonPath("$.data.general").value(containsString("정원: 40명")))
                .andExpect(jsonPath("$.data.general").value(containsString("제공하는 지원: 쉬운 말 안내, 조용한 휴식 공간")))
                .andExpect(jsonPath("$.data.general").value(containsString("편의시설: 승강기, 장애인 화장실")))
                // 확인 필요(UNKNOWN)로 둔 수어 통역은 홍보하지 않는다
                .andExpect(jsonPath("$.data.general").value(not(containsString("수어"))))
                .andExpect(jsonPath("$.data.easy").value(containsString("언제: " + monthDay())))
                .andExpect(jsonPath("$.data.easy").value(containsString("오후 1시부터 4시까지")))
                .andExpect(jsonPath("$.data.easy").value(containsString("휠체어로 다닐 수 있어요.")))
                .andExpect(jsonPath("$.data.easy").value(containsString("쉬고 싶으면 조용한 곳에서 쉴 수 있어요.")))
                .andExpect(jsonPath("$.data.sms").value(containsString(monthDay())))
                .andExpect(jsonPath("$.data.sms").value(containsString("함께 걷는 가을 축제")));
    }

    @Test
    void 쉬운_말에_어려운_낱말이_있거나_일반_홍보문이_정원을_바꾸면_그_글만_초안을_쓴다() throws Exception {
        String token = signUp();
        long eventId = festival(token);
        given(openAiClient.isEnabled()).willReturn(true);
        String ai = objectMapper.writeValueAsString(Map.of(
                "general", "가을 축제에 초대합니다. 정원은 50명입니다.",
                "easy", "가을 축제에 와요.\n언제: " + monthDay() + "\n편의시설이 좋아요.",
                "sms", "[용인 주간보호센터] " + monthDay() + " 함께 걷는 가을 축제"));
        given(openAiClient.completeJson(anyString(), anyString())).willReturn(Optional.of(objectMapper.readTree(ai)));

        write(token, eventId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiWritten").value(true))
                .andExpect(jsonPath("$.data.general").value(containsString("정원: 40명")))
                .andExpect(jsonPath("$.data.easy").value(containsString("휠체어로 다닐 수 있어요.")))
                .andExpect(jsonPath("$.data.sms").value("[용인 주간보호센터] " + monthDay() + " 함께 걷는 가을 축제"));
    }

    @Test
    void 규칙에_맞는_AI_쉬운_말은_그대로_쓴다() throws Exception {
        String token = signUp();
        long eventId = festival(token);
        given(openAiClient.isEnabled()).willReturn(true);
        String easy = "가을 축제에 놀러 와요.\n언제: " + monthDay() + " 오후 1시\n어디: 센터 강당\n휠체어로 다닐 수 있어요.";
        given(openAiClient.completeJson(anyString(), anyString()))
                .willReturn(Optional.of(objectMapper.readTree(objectMapper.writeValueAsString(Map.of("easy", easy)))));

        write(token, eventId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiWritten").value(true))
                .andExpect(jsonPath("$.data.easy").value(easy));
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private byte[] poster(String token, long eventId, String body) throws Exception {
        var request = post("/api/v1/events/{eventId}/promotions/poster", eventId).header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private static boolean hasImage(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        var resources = reader.getPageN(1).getAsDict(PdfName.RESOURCES);
        return resources != null && resources.getAsDict(PdfName.XOBJECT) != null;
    }

    @Test
    void 대표_사진과_행사_정보와_쉬운_말_홍보문을_A4_포스터_PDF로_내려받는다() throws Exception {
        String token = signUp();
        long eventId = festival(token);
        mockMvc.perform(multipart("/api/v1/events/{eventId}/images", eventId)
                        .file(new MockMultipartFile("images", "cover.png", "image/png", png()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        byte[] pdf = poster(token, eventId, null);
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("함께 걷는 가을 축제").contains("정원  40명").contains("센터 강당")
                .contains("휠체어로 다닐 수 있어요.").contains("주최 용인 주간보호센터");
        assertThat(hasImage(pdf)).isTrue();
        verify(openAiClient, never()).completeJson(anyString(), anyString());
    }

    @Test
    void 화면에서_고친_쉬운_말을_보내면_그대로_넣고_사진이_없으면_글만_넣는다() throws Exception {
        String token = signUp();
        long eventId = festival(token);

        byte[] pdf = poster(token, eventId, objectMapper.writeValueAsString(
                Map.of("easy", "가을 축제에 같이 가요.\n맛있는 간식도 있어요.")));
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("맛있는 간식도 있어요.").doesNotContain("휠체어로 다닐 수 있어요.");
        assertThat(hasImage(pdf)).isFalse();
    }

    @Test
    void 포스터도_주최_기관만_내려받는다() throws Exception {
        long eventId = festival(signUp());
        mockMvc.perform(post("/api/v1/events/{eventId}/promotions/poster", eventId).header("Authorization", "Bearer " + signUp()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 주최_기관이_아니면_403() throws Exception {
        long eventId = festival(signUp());
        write(signUp(), eventId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_ACCESS_DENIED"));
    }
}
