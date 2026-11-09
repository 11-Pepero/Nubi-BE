package dev.hidly.nubi.application;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 신청 명단 내려받기(API-49)
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationExportApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SupportTagRepository supportTagRepository;

    private String hostToken;
    private String suji;
    private String giheung;
    private long eventId;
    private long wheelchairId;
    private long easyWordsId;

    @BeforeEach
    void setUp() throws Exception {
        hostToken = signUp("용인 주간보호센터", null);
        suji = signUp("수지 주간보호센터", "031-222-2222");
        giheung = signUp("기흥 장애인복지관", null);
        wheelchairId = tagId("WHEELCHAIR");
        easyWordsId = tagId("EASY_WORDS");
        eventId = readId(ok(mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"개관 30주년 가을 축제\",\"status\":\"OPEN\",\"startAt\":\"%s\"}"
                        .formatted(LocalDateTime.now().plusDays(10).withNano(0))))), "$.data.programId");
    }

    private long tagId(String code) {
        return supportTagRepository.findAll().stream()
                .filter(tag -> tag.getCode().equals(code))
                .map(SupportTag::getId).findFirst().orElseThrow();
    }

    private String signUp(String orgName, String phone) throws Exception {
        String body = """
                {"orgName":"%s","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"Passw0rd!123"%s}
                """.formatted(orgName, "export-" + UUID.randomUUID() + "@nubi.dev", phone == null ? "" : ",\"phone\":\"" + phone + "\"");
        return JsonPath.read(ok(mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(body))), "$.data.accessToken");
    }

    private String ok(ResultActions actions) throws Exception {
        return actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long readId(String response, String path) {
        return ((Number) JsonPath.read(response, path)).longValue();
    }

    private long apply(String token, String groupName, int memberCount, String supports, String extra) throws Exception {
        long groupId = readId(ok(mockMvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"memberCount\":%d,\"supports\":[%s]}".formatted(groupName, memberCount, supports)))),
                "$.data.id");
        return readId(ok(mockMvc.perform(post("/api/v1/programs/{id}/applications", eventId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":%d%s}".formatted(groupId, extra == null ? "" : "," + extra)))), "$.data.applicationId");
    }

    private XSSFWorkbook download(String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events/{id}/applications/export" + query, eventId)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''");
        return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    }

    private String text(Row row, int column) {
        return row.getCell(column).toString();
    }

    @Test
    void 명단을_엑셀로_내려받으면_신청_기관과_개별_케어_시트가_있다() throws Exception {
        long careId = readId(ok(mockMvc.perform(post("/api/v1/care-persons").header("Authorization", "Bearer " + suji)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\":\"하늘\",\"applyToJudgement\":true,\"items\":[{\"content\":\"큰 소리 피하기\"}]}"))), "$.data.id");
        long first = apply(suji, "주간활동반", 6,
                "{\"supportTagId\":%d,\"quantity\":2},{\"supportTagId\":%d}".formatted(wheelchairId, easyWordsId),
                "\"careIds\":[%d],\"companionCount\":2,\"transport\":\"센터 차량\"".formatted(careId));
        long second = apply(giheung, "성인 여가반", 4, "", null);
        mockMvc.perform(patch("/api/v1/events/{e}/applications/{a}/status", eventId, second)
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());

        try (XSSFWorkbook workbook = download("?format=xlsx")) {
            Sheet sheet = workbook.getSheet("신청 기관");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(text(sheet.getRow(0), 1)).isEqualTo("신청 기관");

            Row row = sheet.getRow(1);
            assertThat((long) row.getCell(0).getNumericCellValue()).isEqualTo(first);
            assertThat(text(row, 1)).isEqualTo("수지 주간보호센터");
            assertThat(text(row, 2)).isEqualTo("주간활동반");
            assertThat(text(row, 4)).isEqualTo("031-222-2222");
            assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(6);
            assertThat(row.getCell(6).getNumericCellValue()).isEqualTo(2);
            assertThat(text(row, 7)).isEqualTo("센터 차량");
            assertThat(text(row, 8)).isEqualTo("휠체어 2, 쉬운 말 안내");
            assertThat(row.getCell(9).getNumericCellValue()).isEqualTo(1);
            assertThat(text(row, 10)).isEqualTo("새 신청");
            assertThat(text(sheet.getRow(2), 10)).isEqualTo("확정");

            Sheet cares = workbook.getSheet("개별 케어");
            assertThat(cares.getLastRowNum()).isEqualTo(1);
            assertThat(text(cares.getRow(1), 2)).isEqualTo("하늘");
            assertThat(text(cares.getRow(1), 3)).isEqualTo("큰 소리 피하기");
        }

        // 상태로 거르면 그 상태만
        try (XSSFWorkbook workbook = download("?status=CONFIRMED")) {
            Sheet sheet = workbook.getSheet("신청 기관");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(text(sheet.getRow(1), 1)).isEqualTo("기흥 장애인복지관");
        }
    }

    @Test
    void 거절된_신청은_기본_명단에서_빠지고_신청이_없으면_머리글만_있다() throws Exception {
        try (XSSFWorkbook workbook = download("")) {
            assertThat(workbook.getSheet("신청 기관").getLastRowNum()).isZero();
        }

        long rejected = apply(suji, "주간활동반", 3, "", null);
        mockMvc.perform(patch("/api/v1/events/{e}/applications/{a}/status", eventId, rejected)
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}")).andExpect(status().isOk());
        try (XSSFWorkbook workbook = download("")) {
            assertThat(workbook.getSheet("신청 기관").getLastRowNum()).isZero();
        }
    }

    @Test
    void 주최_기관만_xlsx로만_내려받을_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/events/{id}/applications/export", eventId).header("Authorization", "Bearer " + suji))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{id}/applications/export", eventId).param("format", "csv")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/events/{id}/applications/export", eventId))
                .andExpect(status().isUnauthorized());
    }
}
