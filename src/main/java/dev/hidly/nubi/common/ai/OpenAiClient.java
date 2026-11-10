package dev.hidly.nubi.common.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// OpenAI Chat Completions로 JSON 한 덩어리를 받는다.
// 키가 없거나, 느리거나, 실패하면 빈 값을 돌려주고 부르는 쪽이 미리 써 둔 초안을 쓴다(시연 때 AI 때문에 화면이 깨지지 않게).
@Slf4j
@Component
public class OpenAiClient {
    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenAiClient(ObjectMapper objectMapper,
                        @Value("${external.openai.api-key:}") String apiKey,
                        @Value("${external.openai.model:gpt-5.5}") String model) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gpt-5.5" : model.trim();
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    // system: 역할과 규칙, user: 넘길 사실(JSON 문자열). 응답 본문은 JSON 객체여야 한다.
    public Optional<JsonNode> completeJson(String system, String user) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        long started = System.currentTimeMillis();
        try {
            JsonNode response = restClient.post()
                    .uri(URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String content = response == null ? null
                    : response.path("choices").path(0).path("message").path("content").asString(null);
            if (content == null || content.isBlank()) {
                log.warn("[completeJson] 빈 응답: model={}", model);
                return Optional.empty();
            }
            log.info("[completeJson] model={}, {}ms", model, System.currentTimeMillis() - started);
            return Optional.of(objectMapper.readTree(content));
        } catch (Exception e) {
            log.warn("[completeJson] 호출 실패, 초안 사용: model={}, {}", model, e.getMessage());
            return Optional.empty();
        }
    }
}
