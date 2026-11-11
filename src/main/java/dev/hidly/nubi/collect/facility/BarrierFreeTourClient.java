package dev.hidly.nubi.collect.facility;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 한국관광공사 무장애 여행 정보 (공공데이터포털 15101897, KorWithService2)
// 지역 목록(areaBasedList2)으로 용인시 처인·기흥·수지 시설을 받고, 이어진 시설만 무장애 상세(detailWithTour2)를 읽는다.
// 장애인편의시설 현황에 없는 공연장·박물관 같은 문화시설이 들어 있다.
@Slf4j
@Component
public class BarrierFreeTourClient {
    private static final String BASE = "https://apis.data.go.kr/B551011/KorWithService2";
    private static final String GYEONGGI = "41";
    // 법정동 시군구 코드: 용인시 처인구, 기흥구, 수지구
    public static final List<String> YONGIN_SIGUNGU = List.of("461", "463", "465");
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 5;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // 목록의 시설 한 곳. contentTypeId 14 = 문화시설
    public record Place(String contentId, String title, String contentTypeId, String address, Double latitude, Double longitude) {
    }

    private final RestClient collectRestClient;
    private final String serviceKey;

    // 무장애 여행 정보를 활용신청한 계정의 키(BARRIER_FREE_SERVICE_KEY). 비우면 꺼진다.
    public BarrierFreeTourClient(RestClient collectRestClient,
                                 @Value("${external.data-go-kr.barrier-free-key:}") String serviceKey) {
        this.collectRestClient = collectRestClient;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
    }

    public boolean isEnabled() {
        return !serviceKey.isBlank();
    }

    public List<Place> findYonginPlaces() {
        List<Place> places = new ArrayList<>();
        for (String sigungu : YONGIN_SIGUNGU) {
            List<Place> found = findPlaces(GYEONGGI, sigungu);
            if (found == null) {
                // 한 구라도 못 받으면 일부만으로 편의시설을 덮어쓰지 않도록 비운다.
                return List.of();
            }
            places.addAll(found);
        }
        return places;
    }

    // 실패하면 null
    List<Place> findPlaces(String regionCode, String sigunguCode) {
        List<Place> places = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode body = fetch("/areaBasedList2?numOfRows=" + PAGE_SIZE + "&pageNo=" + page
                    + "&lDongRegnCd=" + regionCode + "&lDongSignguCd=" + sigunguCode);
            if (body == null) {
                return null;
            }
            List<JsonNode> items = items(body);
            for (JsonNode item : items) {
                String id = text(item, "contentid");
                if (id != null) {
                    places.add(new Place(id, text(item, "title"), text(item, "contenttypeid"), text(item, "addr1"),
                            number(text(item, "mapy")), number(text(item, "mapx"))));
                }
            }
            long total = body.path("totalCount").asLong(0);
            if (items.isEmpty() || (long) page * PAGE_SIZE >= total) {
                break;
            }
        }
        return places;
    }

    // 무장애 상세: {"exit": "주 출입구는 턱이 없어 휠체어 접근 가능함", "elevator": "엘리베이터 있음", ...} (빈 값은 뺀다)
    public Map<String, String> findDetail(String contentId) {
        JsonNode body = fetch("/detailWithTour2?contentId=" + encode(contentId));
        return body == null ? Map.of() : detailOf(body);
    }

    static Map<String, String> detailOf(JsonNode body) {
        Map<String, String> detail = new LinkedHashMap<>();
        List<JsonNode> items = items(body);
        if (items.isEmpty()) {
            return detail;
        }
        for (Map.Entry<String, JsonNode> field : items.getFirst().properties()) {
            String value = field.getValue().isString() ? field.getValue().asString().trim() : null;
            if (value != null && !value.isEmpty() && !"contentid".equals(field.getKey())) {
                detail.put(field.getKey(), value);
            }
        }
        return detail;
    }

    // response.body를 돌려준다. 오류 응답이나 요청 실패면 null
    private JsonNode fetch(String path) {
        String encodedKey = URLEncoder.encode(URLDecoder.decode(serviceKey, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        try {
            String raw = collectRestClient.get()
                    .uri(URI.create(BASE + path + "&MobileOS=ETC&MobileApp=nubi&_type=json&serviceKey=" + encodedKey))
                    .retrieve()
                    .body(String.class);
            return raw == null ? null : bodyOf(raw);
        } catch (RuntimeException e) {
            log.warn("[BarrierFreeTourClient] 요청 실패: {}", summarize(e));
            return null;
        }
    }

    static JsonNode bodyOf(String raw) {
        try {
            JsonNode response = JSON.readTree(raw).path("response");
            String code = response.path("header").path("resultCode").asString("");
            if (!"0000".equals(code)) {
                log.warn("[BarrierFreeTourClient] 오류 응답: {}", response.path("header").path("resultMsg").asString(code));
                return null;
            }
            return response.path("body");
        } catch (RuntimeException e) {
            // 인증키 오류는 JSON이 아닌 XML로 온다.
            log.warn("[BarrierFreeTourClient] 응답을 읽지 못함: {}", raw.length() > 120 ? raw.substring(0, 120) : raw);
            return null;
        }
    }

    // items가 비면 ""(빈 문자열), 하나면 객체, 여럿이면 배열로 온다.
    private static List<JsonNode> items(JsonNode body) {
        JsonNode item = body.path("items").path("item");
        List<JsonNode> list = new ArrayList<>();
        if (item.isArray()) {
            item.forEach(list::add);
        } else if (item.isObject()) {
            list.add(item);
        }
        return list;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Double number(String value) {
        try {
            return value == null ? null : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String summarize(RuntimeException e) {
        String message = String.valueOf(e.getMessage()).replaceAll("serviceKey=[^&\"\\s]*", "serviceKey=***").replaceAll("\\s+", " ");
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
