package dev.hidly.nubi.collect.publicdata;

import dev.hidly.nubi.collect.NoticeCollector;
import dev.hidly.nubi.collect.NoticeData;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

@Slf4j
@Component
public class YonginCultureApiCollector implements NoticeCollector {
    public static final String DATASET = "YONGIN_CULTURE_API";
    private static final String URL = "https://api.odcloud.kr/api/15155187/v1/uddi:4e7ca14c-adad-4b18-8143-d7e1de1f3e99";

    private final RestClient collectRestClient;
    private final String serviceKey;

    public YonginCultureApiCollector(RestClient collectRestClient,
                                     @Value("${external.data-go-kr.service-key:}") String serviceKey) {
        this.collectRestClient = collectRestClient;
        this.serviceKey = serviceKey;
    }

    @Override
    public String dataset() {
        return DATASET;
    }

    @Override
    public List<NoticeData> collect(Predicate<String> alreadyCollected) {
        if (serviceKey.isBlank()) {
            log.warn("[YonginCultureApiCollector] 공공데이터 인증키가 없어 건너뜀");
            return List.of();
        }
        String encodedKey = URLEncoder.encode(URLDecoder.decode(serviceKey.trim(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        Map<String, Object> response;
        try {
            response = collectRestClient.get()
                    .uri(URI.create(URL + "?page=1&perPage=1000&serviceKey=" + encodedKey))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RuntimeException e) {
            log.warn("[YonginCultureApiCollector] 요청 실패: {}", summarize(e));
            return List.of();
        }

        List<NoticeData> notices = new ArrayList<>();
        Object data = response == null ? null : response.get("data");
        if (data instanceof List<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map) {
                    NoticeData notice = toNotice(castMap(map));
                    if (notice != null && !alreadyCollected.test(notice.externalId())) {
                        notices.add(notice);
                    }
                }
            }
        }
        log.info("[YonginCultureApiCollector] 새 행사 {}건", notices.size());
        return notices;
    }

    static NoticeData toNotice(Map<String, Object> row) {
        Object id = row.get("행사일련번호");
        LocalDate start = date(row.get("행사시작일"));
        if (id == null || start == null || text(row.get("행사제목")) == null) {
            return null;
        }
        LocalDate end = Optional.ofNullable(date(row.get("행사종료일"))).orElse(start);
        String time = text(row.get("행사시간"));
        return new NoticeData(DATASET, DATASET + "-" + id, row, text(row.get("행사제목")), ProgramCategory.CULTURE,
                start.atStartOfDay(), end.atTime(23, 59),
                text(row.get("행사장소")), text(row.get("행사장소주소")), truncate(text(row.get("행사문의처")), 20),
                time == null ? null : "행사시간: " + time, null);
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static LocalDate date(Object value) {
        String text = text(value);
        if (text == null || text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String summarize(RuntimeException e) {
        String message = String.valueOf(e.getMessage()).replaceAll("serviceKey=[^&\"\\s]*", "serviceKey=***").replaceAll("\\s+", " ");
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
