package dev.hidly.nubi.collect.calendar;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YonginCalendarParser {
    private static final Pattern SHOW_IDX = Pattern.compile("q_showIdx=(\\d+)");
    private static final Pattern DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    public record Detail(String title, LocalDate startDate, LocalDate endDate, Map<String, String> info, String content) {
    }

    private YonginCalendarParser() {
    }

    public static Set<String> parseShowIds(String html) {
        Set<String> ids = new LinkedHashSet<>();
        for (Element link : Jsoup.parse(html).select("a[href*=q_showIdx=]")) {
            Matcher matcher = SHOW_IDX.matcher(link.attr("href"));
            if (matcher.find()) {
                ids.add(matcher.group(1));
            }
        }
        return ids;
    }

    public static Detail parseDetail(String html) {
        Document document = Jsoup.parse(html);
        Element titleElement = document.selectFirst(".detail_title");
        if (titleElement == null || titleElement.text().isBlank()) {
            return null;
        }

        Map<String, String> info = new LinkedHashMap<>();
        for (Element item : document.select("li.info_item")) {
            Element title = item.selectFirst("em.title");
            Element desc = item.selectFirst("em.desc");
            if (title != null && desc != null && !info.containsKey(title.text().trim())) {
                info.put(title.text().trim(), desc.wholeText().trim().replaceAll("\\s*\\n\\s*", " / "));
            }
        }

        Matcher dates = DATE.matcher(info.getOrDefault("일시", ""));
        LocalDate startDate = dates.find() ? LocalDate.parse(dates.group(1)) : null;
        LocalDate endDate = dates.find() ? LocalDate.parse(dates.group(1)) : startDate;

        Element view = document.selectFirst(".view_text");
        String content = view == null ? "" : view.text().trim();
        return new Detail(titleElement.text().trim(), startDate, endDate, info, content);
    }
}
