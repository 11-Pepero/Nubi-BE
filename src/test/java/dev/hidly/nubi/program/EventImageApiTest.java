package dev.hidly.nubi.program;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "file.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
class EventImageApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private ProgramRepository programRepository;

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

    private Long createEvent(Long organizationId) {
        return programRepository.save(Program.builder()
                .hostOrganization(organizationRepository.findById(organizationId).orElseThrow())
                .title("용인 30주년 어울림 한마당")
                .build()).getId();
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("images", "poster.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    void 주최_기관은_행사_사진을_올리고_순서대로_받는다() throws Exception {
        Login host = signUp();
        Long eventId = createEvent(host.organizationId());

        mockMvc.perform(multipart("/api/v1/events/{eventId}/images", eventId).file(png()).file(png())
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data[0].url").value(org.hamcrest.Matchers.startsWith("/uploads/")));

        mockMvc.perform(multipart("/api/v1/events/{eventId}/images", eventId).file(png())
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[2].sortOrder").value(2));
    }

    @Test
    void 사진_순서를_바꾸면_맨_앞이_대표가_된다() throws Exception {
        Login host = signUp();
        Long eventId = createEvent(host.organizationId());
        String uploaded = mockMvc.perform(multipart("/api/v1/events/{eventId}/images", eventId).file(png()).file(png()).file(png())
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> ids = JsonPath.read(uploaded, "$.data[*].imageId");

        mockMvc.perform(patch("/api/v1/events/{eventId}/images/order", eventId)
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[%s,%s,%s]}".formatted(ids.get(2), ids.get(0), ids.get(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].imageId").value(ids.get(2).longValue()))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[2].imageId").value(ids.get(1).longValue()));

        mockMvc.perform(patch("/api/v1/events/{eventId}/images/order", eventId)
                        .header("Authorization", "Bearer " + host.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[%s,%s,%s]}".formatted(ids.get(0), ids.get(0), ids.get(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IMAGE_ORDER_INVALID"));
    }

    @Test
    void 다른_기관은_사진을_올릴_수_없다() throws Exception {
        Long eventId = createEvent(signUp().organizationId());
        Login other = signUp();

        mockMvc.perform(multipart("/api/v1/events/{eventId}/images", eventId).file(png())
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_ACCESS_DENIED"));
    }

    @Test
    void 이미지가_아니면_거절하고_없는_행사면_404() throws Exception {
        Login host = signUp();
        Long eventId = createEvent(host.organizationId());

        mockMvc.perform(multipart("/api/v1/events/{eventId}/images", eventId)
                        .file(new MockMultipartFile("images", "a.pdf", "application/pdf", new byte[]{1}))
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IMAGE_TYPE_NOT_ALLOWED"));

        mockMvc.perform(multipart("/api/v1/events/{eventId}/images", 999_999_999L).file(png())
                        .header("Authorization", "Bearer " + host.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"));
    }
}
