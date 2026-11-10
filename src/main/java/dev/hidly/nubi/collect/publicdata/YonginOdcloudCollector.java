package dev.hidly.nubi.collect.publicdata;

import dev.hidly.nubi.collect.NoticeCollector;
import dev.hidly.nubi.collect.NoticeData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 공공데이터포털이 용인시 파일데이터를 바꿔 주는 API(api.odcloud.kr)를 끝까지 읽는 공통 부분.
// 보건교육, 평생교육, 예절교육관 데이터는 지난 공고가 대부분이라 끝난 상태(ENDED)로 저장되고,
// 접근성 정보 공개율 같은 문제 분석과 AI 구조화 대상으로 쓴다.
@Slf4j
abstract class YonginOdcloudCollector implements NoticeCollector {
    private static final int PER_PAGE = 1000;
    private static final int MAX_PAGES = 10;
    private static final Pattern FULL_DATE = Pattern.compile("(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");

    private final RestClient collectRestClient;
    private final String serviceKey;
    private final String url;

    protected YonginOdcloudCollector(RestClient collectRestClient, String serviceKey, String url) {
        this.collectRestClient = collectRestClient;
        this.serviceKey = serviceKey == null ? "" : serviceKey;
        this.url = url;
    }

    protected abstract NoticeData toNotice(Map<String, Object> row);

    @Override
    public List<NoticeData> collect(Predicate<String> alreadyCollected) {
        String name = getClass().getSimpleName();
        if (serviceKey.isBlank()) {
            log.warn("[{}] 공공데이터 인증키가 없어 건너뜀", name);
            return List.of();
        }
        String encodedKey = URLEncoder.encode(URLDecoder.decode(serviceKey.trim(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        List<NoticeData> notices = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, Object> response;
            try {
                response = collectRestClient.get()
                        .uri(URI.create(url + "?page=" + page + "&perPage=" + PER_PAGE + "&serviceKey=" + encodedKey))
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
            } catch (RuntimeException e) {
                log.warn("[{}] 요청 실패: {}", name, summarize(e));
                break;
            }
            if (response == null || !(response.get("data") instanceof List<?> rows) || rows.isEmpty()) {
                break;
            }
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                    NoticeData notice = toNotice(copy);
                    if (notice != null && !isTestRow(notice.title()) && !alreadyCollected.test(notice.externalId())) {
                        notices.add(notice);
                    }
                }
            }
            long total = response.get("totalCount") instanceof Number n ? n.longValue() : 0;
            if ((long) page * PER_PAGE >= total) {
                break;
            }
        }
        log.info("[{}] 새 공고 {}건", name, notices.size());
        return notices;
    }

    // 원문 시스템을 시험하느라 남긴 행 ("test", "테스트입니다.")
    static boolean isTestRow(String title) {
        return title != null && title.trim().toLowerCase(Locale.ROOT).matches("^(test|테스트|시험)[\\s.!]*(입니다)?[\\s.!]*$");
    }

    // "2019-04-02", "2018. 03. 20." 형식
    protected static LocalDate date(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        Matcher full = FULL_DATE.matcher(text);
        if (!full.find()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(full.group(1)), Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    // "2019-04-02 ~ 2019-06-27", "5월3일 ~ 5월 27일(매주 월,목요일)"처럼 쓴 기간. 연도가 없으면 baseYear를 쓴다.
    protected static List<LocalDate> datesIn(String text, Integer baseYear) {
        List<LocalDate> dates = new ArrayList<>();
        if (text == null) {
            return dates;
        }
        Matcher full = FULL_DATE.matcher(text);
        while (full.find()) {
            try {
                dates.add(LocalDate.of(Integer.parseInt(full.group(1)), Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3))));
            } catch (RuntimeException ignored) {
                // 날짜가 아닌 숫자
            }
        }
        if (dates.isEmpty() && baseYear != null) {
            Matcher monthDay = MONTH_DAY.matcher(text);
            while (monthDay.find()) {
                try {
                    dates.add(LocalDate.of(baseYear, Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2))));
                } catch (RuntimeException ignored) {
                    // 날짜가 아닌 숫자
                }
            }
        }
        return dates;
    }

    protected static LocalTime time(Object value) {
        String text = text(value);
        if (text == null || !text.matches("\\d{1,2}:\\d{2}")) {
            return null;
        }
        String[] parts = text.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return hour < 24 && minute < 60 ? LocalTime.of(hour, minute) : null;
    }

    protected static LocalDateTime at(LocalDate date, LocalTime time, LocalTime fallback) {
        return date == null ? null : date.atTime(time == null ? fallback : time);
    }

    protected static String description(Map<String, String> fields) {
        StringBuilder text = new StringBuilder();
        fields.forEach((label, value) -> {
            if (value != null) {
                text.append(label).append(": ").append(value).append('\n');
            }
        });
        return text.isEmpty() ? null : text.toString().trim();
    }

    // "None", 빈칸은 값이 없는 것으로 본다.
    protected static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || text.equalsIgnoreCase("none") ? null : text;
    }

    private static String summarize(RuntimeException e) {
        String message = String.valueOf(e.getMessage()).replaceAll("serviceKey=[^&\"\\s]*", "serviceKey=***").replaceAll("\\s+", " ");
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
