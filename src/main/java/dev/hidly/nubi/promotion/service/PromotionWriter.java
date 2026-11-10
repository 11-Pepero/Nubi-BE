package dev.hidly.nubi.promotion.service;

import dev.hidly.nubi.common.ai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

// 행사 정보로 홍보문 세 가지(일반 / 쉬운 말 / 문자용) 초안을 쓰고, AI가 같은 사실만으로 다듬게 한다.
// 쉬운 말은 발달장애인이 읽는 글이라 줄 길이와 어려운 낱말을 코드로 검사하고, 어기면 초안을 쓴다.
@Component
@RequiredArgsConstructor
public class PromotionWriter {
    static final int EASY_MAX_LINE = 35;
    static final int EASY_MAX_LINES = 15;
    static final int SMS_MAX_LENGTH = 100;
    static final int GENERAL_MAX_LENGTH = 1000;
    static final List<String> HARD_WORDS = List.of("접근성", "편의시설", "배리어프리", "무장애", "보조인", "참가비 면제", "선착순");

    // 지원 항목·편의시설을 쉬운 말 한 문장으로. 없는 코드는 "OO 지원이 있어요."
    private static final Map<String, String> EASY_SENTENCES = Map.ofEntries(
            Map.entry("WHEELCHAIR", "휠체어로 다닐 수 있어요."),
            Map.entry("POWER_WHEELCHAIR", "휠체어로 다닐 수 있어요."),
            Map.entry("NO_STAIRS", "휠체어로 다닐 수 있어요."),
            Map.entry("ELEVATOR", "휠체어로 다닐 수 있어요."),
            Map.entry("RAMP", "휠체어로 다닐 수 있어요."),
            Map.entry("NO_STEP", "휠체어로 다닐 수 있어요."),
            Map.entry("ACCESSIBLE_TOILET", "장애인 화장실이 있어요."),
            Map.entry("PARKING", "장애인 주차장이 있어요."),
            Map.entry("EASY_WORDS", "쉬운 말로 안내해 드려요."),
            Map.entry("PICTURE_BOARD", "그림으로 안내해 드려요."),
            Map.entry("SIGN_LANGUAGE", "수어로 안내해 드려요."),
            Map.entry("QUIET_REST_SPACE", "쉬고 싶으면 조용한 곳에서 쉴 수 있어요."),
            Map.entry("LOW_STIMULUS", "큰 소리와 밝은 빛을 줄였어요."),
            Map.entry("COMPANION", "도와주는 사람이 함께 있어요."),
            Map.entry("ONE_TO_ONE", "도와주는 사람이 함께 있어요."),
            Map.entry("MEAL_ASSIST", "밥 먹을 때 도와드려요."));

    static final String SYSTEM_PROMPT = """
            너는 용인시 장애인복지시설이 여는 행사의 홍보문을 다듬는 도우미다.
            입력으로 facts(행사 사실)와 drafts(초안)가 JSON으로 온다. 규칙:
            1. facts에 없는 사실(날짜, 시간, 장소, 정원, 지원, 편의시설, 가격)을 새로 만들지 않는다. 날짜와 정원 숫자는 facts 그대로 쓴다.
            2. general: 다른 기관 담당자가 읽는 일반 홍보 문구. 행사 소개, 일시, 장소, 정원, 제공하는 지원과 편의시설을 담는다. 600자 이내.
            3. easy: 발달장애인이 직접 읽는 쉬운 말 버전. 한 줄에 한 가지만, 한 줄은 30자 이내, 15줄 이내로 쓴다.
               "언제:", "어디:" 줄을 넣고, 지원은 "휠체어로 다닐 수 있어요."처럼 생활 말로 쓴다.
               접근성, 편의시설, 배리어프리, 무장애, 보조인, 선착순 같은 어려운 말은 쓰지 않는다.
            4. sms: 문자·포스터용 짧은 문구. 날짜, 행사 이름, 장소를 넣고 90자 이내로 쓴다.
            5. 응답은 JSON 객체 하나로만 한다: {"general": "...", "easy": "...", "sms": "..."}
            """;

    public record Facts(String title, String hostName, LocalDateTime startAt, LocalDateTime endAt, String venueName,
                        Integer capacity, String description, List<String> supports, List<String> supportCodes,
                        List<String> facilities, List<String> facilityCodes) {
    }

    public record Result(String general, String easy, String sms, boolean aiWritten) {
    }

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public Result write(Facts facts) {
        Map<String, String> drafts = new LinkedHashMap<>();
        drafts.put("general", general(facts));
        drafts.put("easy", easy(facts));
        drafts.put("sms", sms(facts));
        if (!openAiClient.isEnabled()) {
            return new Result(drafts.get("general"), drafts.get("easy"), drafts.get("sms"), false);
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("facts", facts);
        input.put("drafts", drafts);
        Optional<JsonNode> response = openAiClient.completeJson(SYSTEM_PROMPT, objectMapper.writeValueAsString(input));
        if (response.isEmpty()) {
            return new Result(drafts.get("general"), drafts.get("easy"), drafts.get("sms"), false);
        }
        String general = pick(response.get(), "general", drafts, text -> isUsableGeneral(facts, text));
        String easy = pick(response.get(), "easy", drafts, text -> isUsableEasy(facts, text));
        String sms = pick(response.get(), "sms", drafts, text -> text.length() <= SMS_MAX_LENGTH);
        boolean anyAi = !general.equals(drafts.get("general")) || !easy.equals(drafts.get("easy")) || !sms.equals(drafts.get("sms"));
        return new Result(general, easy, sms, anyAi);
    }

    private static String pick(JsonNode response, String field, Map<String, String> drafts,
                               Predicate<String> usable) {
        String written = response.path(field).asString("").trim();
        return !written.isBlank() && usable.test(written) ? written : drafts.get(field);
    }

    // 날짜·정원 숫자가 그대로 남아 있어야 한다(AI가 사실을 바꾸지 못하게).
    static boolean isUsableGeneral(Facts facts, String text) {
        if (text.length() > GENERAL_MAX_LENGTH) {
            return false;
        }
        boolean dateKept = facts.startAt() == null || text.contains(monthDay(facts.startAt()));
        boolean capacityKept = facts.capacity() == null || text.contains(facts.capacity() + "명");
        return dateKept && capacityKept;
    }

    static boolean isUsableEasy(Facts facts, String text) {
        List<String> lines = text.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        if (lines.isEmpty() || lines.size() > EASY_MAX_LINES) {
            return false;
        }
        if (lines.stream().anyMatch(line -> line.length() > EASY_MAX_LINE)) {
            return false;
        }
        if (HARD_WORDS.stream().anyMatch(text::contains)) {
            return false;
        }
        return facts.startAt() == null || text.contains(monthDay(facts.startAt()));
    }

    String general(Facts facts) {
        List<String> lines = new ArrayList<>();
        lines.add("%s에 초대합니다.".formatted(facts.title()));
        if (facts.description() != null && !facts.description().isBlank()) {
            lines.add(facts.description().trim());
        }
        lines.add("");
        if (facts.startAt() != null) {
            lines.add("일시: " + generalWhen(facts));
        }
        if (facts.venueName() != null) {
            lines.add("장소: " + facts.venueName());
        }
        if (facts.capacity() != null) {
            lines.add("정원: %d명".formatted(facts.capacity()));
        }
        if (!facts.supports().isEmpty()) {
            lines.add("제공하는 지원: " + String.join(", ", facts.supports()));
        }
        if (!facts.facilities().isEmpty()) {
            lines.add("편의시설: " + String.join(", ", facts.facilities()));
        }
        if (facts.hostName() != null) {
            lines.add("주최: " + facts.hostName());
        }
        return String.join("\n", lines);
    }

    String easy(Facts facts) {
        List<String> lines = new ArrayList<>();
        lines.add("%s에 놀러 오세요.".formatted(facts.title()));
        lines.add("");
        if (facts.startAt() != null) {
            lines.add("언제: " + easyWhen(facts));
        }
        if (facts.venueName() != null) {
            lines.add("어디: " + facts.venueName());
        }
        Set<String> sentences = new LinkedHashSet<>();
        for (int i = 0; i < facts.supportCodes().size(); i++) {
            sentences.add(EASY_SENTENCES.getOrDefault(facts.supportCodes().get(i), facts.supports().get(i) + " 지원이 있어요."));
        }
        facts.facilityCodes().forEach(code -> {
            String sentence = EASY_SENTENCES.get(code);
            if (sentence != null) {
                sentences.add(sentence);
            }
        });
        if (!sentences.isEmpty()) {
            lines.add("");
            lines.addAll(sentences);
        }
        return String.join("\n", lines);
    }

    String sms(Facts facts) {
        StringBuilder text = new StringBuilder();
        if (facts.hostName() != null) {
            text.append('[').append(facts.hostName()).append("] ");
        }
        if (facts.startAt() != null) {
            text.append(monthDay(facts.startAt())).append('(').append(dayShort(facts.startAt())).append(") ");
        }
        text.append(facts.title());
        if (facts.venueName() != null) {
            text.append(" · ").append(facts.venueName());
        }
        if (facts.capacity() != null) {
            text.append(" · 정원 ").append(facts.capacity()).append("명");
        }
        String result = text.toString();
        return result.length() <= SMS_MAX_LENGTH ? result : result.substring(0, SMS_MAX_LENGTH - 1) + "…";
    }

    static String monthDay(LocalDateTime at) {
        return "%d월 %d일".formatted(at.getMonthValue(), at.getDayOfMonth());
    }

    private static String dayShort(LocalDateTime at) {
        return at.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
    }

    // 11월 21일(토) 13:00~16:00
    private static String generalWhen(Facts facts) {
        String when = "%s(%s) %02d:%02d".formatted(monthDay(facts.startAt()), dayShort(facts.startAt()),
                facts.startAt().getHour(), facts.startAt().getMinute());
        if (facts.endAt() != null && facts.endAt().toLocalDate().equals(facts.startAt().toLocalDate())) {
            when += "~%02d:%02d".formatted(facts.endAt().getHour(), facts.endAt().getMinute());
        }
        return when;
    }

    // 11월 21일 토요일, 오후 1시부터 4시까지
    private static String easyWhen(Facts facts) {
        String when = "%s %s, %s".formatted(monthDay(facts.startAt()),
                facts.startAt().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN), easyTime(facts.startAt()));
        if (facts.endAt() != null && facts.endAt().toLocalDate().equals(facts.startAt().toLocalDate())) {
            String end = easyTime(facts.endAt());
            boolean sameHalf = (facts.startAt().getHour() < 12) == (facts.endAt().getHour() < 12);
            when += "부터 " + (sameHalf ? end.substring(3) : end) + "까지";
        }
        return when;
    }

    private static String easyTime(LocalDateTime at) {
        int hour = at.getHour();
        String half = hour < 12 ? "오전" : "오후";
        int h12 = hour % 12 == 0 ? 12 : hour % 12;
        return at.getMinute() == 0 ? "%s %d시".formatted(half, h12) : "%s %d시 %d분".formatted(half, h12, at.getMinute());
    }
}
