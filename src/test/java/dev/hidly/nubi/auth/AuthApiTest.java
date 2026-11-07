package dev.hidly.nubi.auth;

import com.jayway.jsonpath.JsonPath;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {
    private static final String PASSWORD = "Passw0rd!123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@nubi.dev";
    }

    private String signUpBody(String email) {
        return """
                {"orgName":"용인 주간활동센터","type":"DAY_CARE","address":"용인시 처인구 중부대로 1199","email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);
    }

    private String signUp(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 기관_가입하면_ADMIN_담당자와_토큰이_발급된다() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.organizationName").value("용인 주간활동센터"))
                .andExpect(jsonPath("$.data.organizationId").isNumber())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString());
    }

    @Test
    void 대소문자만_다른_이메일로_가입하면_409() throws Exception {
        String email = uniqueEmail();
        signUp(email);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATION"));
    }

    @Test
    void 기관_유형이_잘못되면_400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(uniqueEmail()).replace("DAY_CARE", "HOSPITAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 로그인하면_토큰이_발급된다() throws Exception {
        String email = uniqueEmail();
        signUp(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void 비밀번호가_틀리거나_없는_이메일이면_같은_401() throws Exception {
        String email = uniqueEmail();
        signUp(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(uniqueEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 리프레시_토큰으로_재발급되고_이전_리프레시_토큰은_다시_쓸_수_없다() throws Exception {
        String refreshToken = JsonPath.read(signUp(uniqueEmail()), "$.data.refreshToken");
        String body = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void 액세스_토큰으로는_재발급할_수_없다() throws Exception {
        String accessToken = JsonPath.read(signUp(uniqueEmail()), "$.data.accessToken");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void 리프레시_토큰으로는_보호된_API에_접근할_수_없다() throws Exception {
        String response = signUp(uniqueEmail());
        String accessToken = JsonPath.read(response, "$.data.accessToken");
        String refreshToken = JsonPath.read(response, "$.data.refreshToken");

        mockMvc.perform(get("/api/v1/not-exists").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/not-exists").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 로그아웃하면_액세스_토큰과_리프레시_토큰을_더_쓸_수_없다() throws Exception {
        String response = signUp(uniqueEmail());
        String accessToken = JsonPath.read(response, "$.data.accessToken");
        String refreshToken = JsonPath.read(response, "$.data.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/not-exists").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 주소에_구_이름이_있으면_기관의_구가_채워진다() throws Exception {
        Number organizationId = JsonPath.read(signUp(uniqueEmail()), "$.data.organizationId");

        Organization organization = organizationRepository.findById(organizationId.longValue()).orElseThrow();
        assertThat(organization.getDistrict()).isEqualTo(District.CHEOIN);
    }

    @Test
    void 주소에_용인시_구_이름이_없으면_구는_비어_있다() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(uniqueEmail()).replace("용인시 처인구 중부대로 1199", "서울시 중구 세종대로 110")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number organizationId = JsonPath.read(response, "$.data.organizationId");

        Organization organization = organizationRepository.findById(organizationId.longValue()).orElseThrow();
        assertThat(organization.getDistrict()).isNull();
    }
}
