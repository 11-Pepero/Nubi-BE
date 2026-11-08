package dev.hidly.nubi.collect.calendar;

import dev.hidly.nubi.collect.CollectProperties;
import dev.hidly.nubi.collect.NoticeCollector;
import dev.hidly.nubi.collect.NoticeData;
import dev.hidly.nubi.collect.calendar.YonginCalendarParser.Detail;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.YearMonth;
import java.util.*;
import java.util.function.Predicate;

@Slf4j
@Component
@RequiredArgsConstructor
public class YonginCalendarCollector implements NoticeCollector {
    public static final String DATASET = "YONGIN_CALENDAR";
    private static final String BASE = "https://www.yongin.go.kr/partInfo/cultureArt/";

    private final RestClient collectRestClient;
    private final CollectProperties properties;

    @Override
    public String dataset() {
        return DATASET;
    }

    @Override
    public List<NoticeData> collect(Predicate<String> alreadyCollected) {
        Set<String> showIds = new LinkedHashSet<>();
        YearMonth month = YearMonth.now();
        for (int i = 0; i < properties.calendarMonths(); i++, month = month.plusMonths(1)) {
            String html = fetch("BD_selectCultureArtCalList.do?q_year=" + month.getYear() + "&q_month=" + month.getMonthValue());
            if (html != null) {
                showIds.addAll(YonginCalendarParser.parseShowIds(html));
            }
        }

        List<NoticeData> notices = new ArrayList<>();
        for (String showId : showIds) {
            if (alreadyCollected.test(externalId(showId))) {
                continue;
            }
            String url = BASE + "BD_selectCultureArt.do?q_showIdx=" + showId;
            String html = fetch("BD_selectCultureArt.do?q_showIdx=" + showId);
            Detail detail = html == null ? null : YonginCalendarParser.parseDetail(html);
            if (detail == null || detail.startDate() == null) {
                continue;
            }
            notices.add(toNotice(showId, url, detail));
        }
        log.info("[YonginCalendarCollector] 달력 행사 {}건 중 새 행사 {}건", showIds.size(), notices.size());
        return notices;
    }

    static String externalId(String showId) {
        return DATASET + "-" + showId;
    }

    static NoticeData toNotice(String showId, String url, Detail detail) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("showIdx", showId);
        raw.put("url", url);
        raw.put("title", detail.title());
        raw.put("info", detail.info());
        raw.put("content", detail.content());

        StringBuilder description = new StringBuilder();
        detail.info().forEach((key, value) -> {
            if (!value.isBlank() && !key.equals("일시") && !key.equals("장소")) {
                description.append(key).append(": ").append(value).append('\n');
            }
        });
        if (!detail.content().isBlank()) {
            description.append('\n').append(detail.content());
        }

        return new NoticeData(DATASET, externalId(showId), raw, detail.title(), ProgramCategory.CULTURE,
                detail.startDate().atStartOfDay(), detail.endDate().atTime(23, 59),
                detail.info().get("장소"), null, null, description.toString().trim(), url);
    }

    private String fetch(String path) {
        try {
            Thread.sleep(properties.requestDelayMs());
            return collectRestClient.get().uri(BASE + path).retrieve().body(String.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            log.warn("[YonginCalendarCollector] 요청 실패: {} ({})", path, summarize(e));
            return null;
        }
    }

    private static String summarize(RuntimeException e) {
        String message = String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
