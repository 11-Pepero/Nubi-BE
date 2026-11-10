package dev.hidly.nubi.collect.publicdata;

import dev.hidly.nubi.collect.NoticeCollector;
import dev.hidly.nubi.collect.NoticeData;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Predicate;

// 경기데이터드림 "장애인복지관 운영 프로그램" (DspsnCmwelfctOpertProg) 중 용인시 복지관 3곳의 상시 프로그램.
// 날짜가 정해진 행사가 아니라 일정이 비어 있고, 원문 기준일이 오래되어(2019) 운영 여부는 복지관에 확인해야 한다.
@Slf4j
@Component
public class YonginWelfareCenterProgramCollector implements NoticeCollector {
    public static final String DATASET = "GG_WELFARE_CENTER_PROGRAM";
    private static final String URL = "https://openapi.gg.go.kr/DspsnCmwelfctOpertProg";
    private static final String SERVICE = "DspsnCmwelfctOpertProg";
    private static final String SIGUN = "용인시";
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 5;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient collectRestClient;
    private final String apiKey;

    public YonginWelfareCenterProgramCollector(RestClient collectRestClient, @Value("${external.gg-data.key:}") String apiKey) {
        this.collectRestClient = collectRestClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public String dataset() {
        return DATASET;
    }

    @Override
    public List<NoticeData> collect(Predicate<String> alreadyCollected) {
        if (apiKey.isBlank()) {
            log.warn("[YonginWelfareCenterProgramCollector] 경기데이터드림 인증키(GG_DATA_KEY)가 없어 건너뜀");
            return List.of();
        }
        List<NoticeData> notices = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<Map<String, Object>> rows;
            long total;
            try {
                String body = collectRestClient.get()
                        .uri(URI.create(URL + "?Type=json&pIndex=" + page + "&pSize=" + PAGE_SIZE
                                + "&SIGUN_NM=" + URLEncoder.encode(SIGUN, StandardCharsets.UTF_8)
                                + "&KEY=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
                        .retrieve()
                        .body(String.class);
                Map<String, Object> response = body == null ? Map.of() : JSON.readValue(body, new TypeReference<>() {
                });
                rows = rows(response);
                total = total(response);
            } catch (RuntimeException e) {
                log.warn("[YonginWelfareCenterProgramCollector] 요청 실패: {}", summarize(e));
                break;
            }
            for (Map<String, Object> row : rows) {
                NoticeData notice = toNotice(row);
                if (notice != null && !alreadyCollected.test(notice.externalId())) {
                    notices.add(notice);
                }
            }
            if (rows.isEmpty() || (long) page * PAGE_SIZE >= total) {
                break;
            }
        }
        log.info("[YonginWelfareCenterProgramCollector] 새 프로그램 {}건", notices.size());
        return notices;
    }

    static NoticeData toNotice(Map<String, Object> row) {
        String center = text(row.get("CMWELFCT_NM_INFO"));
        String title = text(row.get("PROG_TITLE"));
        if (center == null || title == null) {
            return null;
        }
        return new NoticeData(DATASET, externalId(row), row, title,
                category(text(row.get("PROG_DIV_NM")), title),
                null, null, center, text(row.get("REFINE_ROADNM_ADDR")),
                truncate(text(row.get("TELNO")), 20),
                description(row), null);
    }

    // 원문에 번호가 없어 복지관·프로그램명·구분·이용시간으로 같은 프로그램을 알아본다.
    static String externalId(Map<String, Object> row) {
        String key = String.join("|", Objects.toString(row.get("CMWELFCT_NM_INFO"), ""), Objects.toString(row.get("PROG_TITLE"), ""),
                Objects.toString(row.get("PROG_DIV_NM"), ""), Objects.toString(row.get("DETAIL_DIV_NM"), ""),
                Objects.toString(row.get("USE_TM_INFO"), ""));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return DATASET + "-" + HexFormat.of().formatHex(hash, 0, 10);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static ProgramCategory category(String division, String title) {
        String text = Objects.toString(division, "") + " " + Objects.toString(title, "");
        if (text.contains("스포츠") || text.contains("체육") || text.contains("운동") || text.contains("수영")) {
            return ProgramCategory.SPORTS;
        }
        if (text.contains("의료") || text.contains("치료") || text.contains("건강")) {
            return ProgramCategory.HEALTH;
        }
        if (text.contains("교육") || text.contains("직업") || text.contains("교실") || text.contains("학습")) {
            return ProgramCategory.EDUCATION;
        }
        return ProgramCategory.CULTURE;
    }

    private static String description(Map<String, Object> row) {
        StringBuilder text = new StringBuilder();
        append(text, "구분", join(text(row.get("PROG_DIV_NM")), text(row.get("DETAIL_DIV_NM"))));
        append(text, "내용", text(row.get("PROG_CONT")));
        append(text, "이용대상", text(row.get("USE_TARGET")));
        append(text, "장애유형", text(row.get("USE_TARGET_OBSTCL_TYPE_COND")));
        append(text, "연령", text(row.get("USE_TARGET_AGE_LIMITN_COND")));
        append(text, "기타조건", text(row.get("USE_TARGET_ETC_COND")));
        append(text, "이용시간", text(row.get("USE_TM_INFO")));
        append(text, "이용금액", amount(text(row.get("USE_AMT"))));
        append(text, "이용금액 기준", text(row.get("USE_AMT_CALC_STD_INFO")));
        append(text, "부가비용", text(row.get("ADDITN_EXPN_INFO")));
        append(text, "유의사항", text(row.get("ATENTN_MATR")));
        append(text, "추가 연락처", text(row.get("ADD_CONTCT_NO_INFO")));
        String standardDate = text(row.get("DATA_STD_DE"));
        text.append("\n상시 운영 프로그램입니다. 원문 기준일").append(standardDate == null ? "" : " " + formatDate(standardDate))
                .append(" 이후 바뀌었을 수 있으니 복지관에 운영 여부를 확인해 주세요.");
        return text.toString().trim();
    }

    private static String amount(String value) {
        if (value == null) {
            return null;
        }
        if (value.matches("\\d+")) {
            return value.equals("0") ? "무료" : String.format("%,d원", Long.parseLong(value));
        }
        return value;
    }

    private static String formatDate(String yyyymmdd) {
        return yyyymmdd.matches("\\d{8}")
                ? yyyymmdd.substring(0, 4) + "-" + yyyymmdd.substring(4, 6) + "-" + yyyymmdd.substring(6)
                : yyyymmdd;
    }

    private static String join(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + " · " + b;
    }

    private static void append(StringBuilder text, String label, String value) {
        if (value != null) {
            text.append(label).append(": ").append(value).append('\n');
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rows(Map<String, Object> response) {
        if (!(response.get(SERVICE) instanceof List<?> parts)) {
            return List.of();
        }
        for (Object part : parts) {
            if (part instanceof Map<?, ?> map && map.get("row") instanceof List<?> list) {
                return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
            }
        }
        return List.of();
    }

    static long total(Map<String, Object> response) {
        if (response.get(SERVICE) instanceof List<?> parts) {
            for (Object part : parts) {
                if (part instanceof Map<?, ?> map && map.get("head") instanceof List<?> head) {
                    for (Object item : head) {
                        if (item instanceof Map<?, ?> h && h.get("list_total_count") instanceof Number n) {
                            return n.longValue();
                        }
                    }
                }
            }
        }
        return 0;
    }

    // "None"처럼 빈 값을 글자로 보내는 칸이 있다.
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || text.equalsIgnoreCase("none") || text.equals("-") ? null : text;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String summarize(RuntimeException e) {
        String message = String.valueOf(e.getMessage()).replaceAll("KEY=[^&\"\\s]*", "KEY=***").replaceAll("\\s+", " ");
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
