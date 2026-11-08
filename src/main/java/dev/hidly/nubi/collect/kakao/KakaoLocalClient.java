package dev.hidly.nubi.collect.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class KakaoLocalClient {
    private static final String KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    public record Place(String name, String address, double latitude, double longitude) {
    }

    private final RestClient collectRestClient;
    private final String restApiKey;

    public KakaoLocalClient(RestClient collectRestClient, @Value("${external.kakao.rest-api-key:}") String restApiKey) {
        this.collectRestClient = collectRestClient;
        this.restApiKey = restApiKey;
    }

    public boolean isEnabled() {
        return !restApiKey.isBlank();
    }

    public Optional<Place> search(String query) {
        if (!isEnabled() || query == null || query.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> response = collectRestClient.get()
                    .uri(UriComponentsBuilder.fromUriString(KEYWORD_URL).queryParam("query", query).queryParam("size", 5)
                            .encode().build().toUri())
                    .header("Authorization", "KakaoAK " + restApiKey.trim())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            Object documents = response == null ? null : response.get("documents");
            if (documents instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .filter(document -> !String.valueOf(document.get("place_name")).contains("주차장"))
                        .findFirst()
                        .map(KakaoLocalClient::toPlace);
            }
        } catch (RuntimeException e) {
            log.warn("[KakaoLocalClient] 장소 검색 실패: {} ({})", query, summarize(e));
        }
        return Optional.empty();
    }

    private static Place toPlace(Map<?, ?> document) {
        String road = (String) document.get("road_address_name");
        String address = road == null || road.isBlank() ? (String) document.get("address_name") : road;
        return new Place((String) document.get("place_name"), address,
                Double.parseDouble(String.valueOf(document.get("y"))), Double.parseDouble(String.valueOf(document.get("x"))));
    }

    private static String summarize(RuntimeException e) {
        String message = String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
