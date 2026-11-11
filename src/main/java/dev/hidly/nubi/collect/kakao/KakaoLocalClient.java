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
    private static final String REGION_CODE_URL = "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json";

    public record Place(String name, String address, double latitude, double longitude) {
    }

    // 장소 고르기(행사 등록)에서 보여 줄 카카오 장소 한 곳
    public record PlaceCandidate(String id, String name, String category, String roadAddress, String address,
                                 String phone, double latitude, double longitude) {
        // 도로명 주소가 있으면 그것, 없으면 지번 주소
        public String displayAddress() {
            return roadAddress == null || roadAddress.isBlank() ? address : roadAddress;
        }
    }

    // 용인시를 덮는 사각형(경도, 위도). 키워드 검색을 이 안으로 줄인다.
    static final String YONGIN_RECT = "126.95,37.08,127.43,37.40";
    private static final int MAX_CANDIDATES = 15;

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

    // 행사 장소 고르기: 용인시 범위에서 키워드로 장소를 찾는다. 정확도 순, 최대 15곳.
    public List<PlaceCandidate> searchPlaces(String query, int size) {
        if (!isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> response = collectRestClient.get()
                    .uri(UriComponentsBuilder.fromUriString(KEYWORD_URL)
                            .queryParam("query", query.trim())
                            .queryParam("rect", YONGIN_RECT)
                            .queryParam("size", Math.max(1, Math.min(size, MAX_CANDIDATES)))
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
                        .map(KakaoLocalClient::toCandidate)
                        // 같은 건물의 주차장·매표소 같은 부속 장소는 행사 장소가 아니라서 뺀다
                        .filter(place -> !isAuxiliary(place))
                        .toList();
            }
        } catch (RuntimeException e) {
            log.warn("[KakaoLocalClient] 장소 고르기 검색 실패: {} ({})", query, summarize(e));
        }
        return List.of();
    }

    // 좌표가 속한 행정동 코드(10자리). 등록장애인 통계가 행정동 기준이라 법정동(B)이 아닌 H를 쓴다.
    public Optional<String> findAdministrativeDongCode(double latitude, double longitude) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> response = collectRestClient.get()
                    .uri(UriComponentsBuilder.fromUriString(REGION_CODE_URL).queryParam("x", longitude).queryParam("y", latitude)
                            .build().toUri())
                    .header("Authorization", "KakaoAK " + restApiKey.trim())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            Object documents = response == null ? null : response.get("documents");
            if (documents instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .filter(document -> "H".equals(document.get("region_type")))
                        .map(document -> String.valueOf(document.get("code")))
                        .findFirst();
            }
        } catch (RuntimeException e) {
            log.warn("[KakaoLocalClient] 행정동 조회 실패: {}, {} ({})", latitude, longitude, summarize(e));
        }
        return Optional.empty();
    }

    private static Place toPlace(Map<?, ?> document) {
        String road = (String) document.get("road_address_name");
        String address = road == null || road.isBlank() ? (String) document.get("address_name") : road;
        return new Place((String) document.get("place_name"), address,
                Double.parseDouble(String.valueOf(document.get("y"))), Double.parseDouble(String.valueOf(document.get("x"))));
    }

    static boolean isAuxiliary(PlaceCandidate place) {
        String name = place.name() == null ? "" : place.name();
        String category = place.category() == null ? "" : place.category();
        return category.contains("주차장") || name.endsWith("주차장") || category.equals("매표소");
    }

    static PlaceCandidate toCandidate(Map<?, ?> document) {
        String category = blankToNull(document.get("category_name"));
        // "문화,예술 > 공연장" 처럼 오니 마지막 분류만 쓴다
        if (category != null && category.contains(">")) {
            category = category.substring(category.lastIndexOf('>') + 1).trim();
        }
        return new PlaceCandidate(
                blankToNull(document.get("id")),
                blankToNull(document.get("place_name")),
                category,
                blankToNull(document.get("road_address_name")),
                blankToNull(document.get("address_name")),
                blankToNull(document.get("phone")),
                Double.parseDouble(String.valueOf(document.get("y"))),
                Double.parseDouble(String.valueOf(document.get("x"))));
    }

    private static String blankToNull(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private static String summarize(RuntimeException e) {
        String message = String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
