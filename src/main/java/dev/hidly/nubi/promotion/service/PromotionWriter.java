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

    // 분야별 첫 줄. AI 없이 쓸 때도 "공지"가 아니라 "가 보고 싶은 글"로 시작한다.
    private static final Map<String, String> HOOKS = Map.of(
            "CULTURE", "같이 보고, 듣고, 즐기는 하루",
            "SPORTS", "몸을 움직이면 기분도 좋아져요",
            "EDUCATION", "새로운 걸 같이 배워 봐요",
            "HEALTH", "건강한 하루를 같이 만들어요",
            "BENEFIT", "놓치면 아까운 혜택이에요");

    static final String SYSTEM_PROMPT = """
            너는 용인시 장애인과 가족, 복지기관 담당자가 글을 보자마자 "어, 재밌겠다. 가 보자"라고 느끼게 만드는 행사 홍보 카피라이터다.
            입력으로 facts(행사 사실)와 drafts(사실만 정리한 초안)가 JSON으로 온다. drafts는 사실 확인용이고 말투를 따라 하지 않는다.

            [목표] 세 글 모두 다음을 해낸다.
            - 첫 줄에서 눈길을 끈다: 궁금증, 설렘, 그날의 한 장면.
            - 거기서 무엇을 하는지 머릿속에 그려지게 한다: title과 description의 활동을 생생한 말로 풀어 쓴다.
            - 언제, 어디서, 어떻게 신청하는지 바로 알 수 있다.

            [사실 규칙] 어기면 글을 버린다.
            - facts에 없는 날짜, 시간, 장소, 정원, 가격, 경품, 간식, 공연, 지원, 편의시설을 만들지 않는다. 활동은 title과 description에 나온 것만 쓴다.
            - 날짜는 "11월 21일"처럼 facts의 월·일 숫자를 그대로, 정원은 "40명"처럼 그대로 쓴다.
            - 무료, 선착순, 마감 임박, 당첨 같은 말은 facts에 그 사실이 있을 때만 쓴다.

            [피할 말투] 보이스피싱·스팸 문자처럼 보이면 실패다.
            - "초대합니다", "안내드립니다", "많은 참여 바랍니다", "신청하세요!!" 같은 공문·광고 문구를 쓰지 않는다.
            - 느낌표는 글 전체에 두 개까지, 이모지는 general에 정보 줄 머리표로만 쓴다. easy와 sms에는 이모지를 쓰지 않는다.
            - 링크, 전화번호, 계좌를 지어내지 않는다.

            [general] 다른 기관 담당자가 읽고 이용자에게 권할 글. 600자 이내.
            1줄: 헤드라인 25자 이내. 질문이나 장면으로 시작한다(예: "올가을, 단풍길을 같이 걸어 볼까요?").
            빈 줄 다음 2~3문장: 그날 무엇을 하고 어떤 기분일지.
            빈 줄 다음 정보 줄: "📅 일시: ...", "📍 장소: ...", "👥 정원: N명", 지원과 편의시설이 있으면 "🤝 이렇게 준비했어요: ..." 한 줄.
            마지막: facts에 있을 때만 신청 방법, 신청 마감, 문의 전화, 주최.

            [easy] 발달장애인이 직접 읽는 쉬운 말. 한 줄에 한 가지, 한 줄 30자 이내, 15줄 이내.
            1줄은 "같이 ○○ 해요!"처럼 무엇을 하는지 바로 알 수 있게 쓴다.
            그다음 1~3줄로 거기서 하는 일을 쉬운 말로, 그다음 "언제:", "어디:" 줄, 그다음 지원을 "휠체어로 다닐 수 있어요."처럼 생활 말로 쓴다.
            접근성, 편의시설, 배리어프리, 무장애, 보조인, 선착순 같은 어려운 말은 쓰지 않는다.

            [sms] 문자 한 통. 90자 이내. 기관 이름이 있으면 맨 앞에 [기관 이름]. 행사 이름보다 "무엇을 하는지"가 먼저 보이게 쓰고 날짜와 장소를 넣는다.

            응답은 JSON 객체 하나로만 한다: {"general": "...", "easy": "...", "sms": "..."}
            """;

    public record Facts(String title, String hostName, LocalDateTime startAt, LocalDateTime endAt, String venueName,
                        Integer capacity, String description, List<String> supports, List<String> supportCodes,
                        List<String> facilities, List<String> facilityCodes,
                        String category, LocalDateTime deadlineAt, String applyMethod, String contactPhone) {
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
        lines.add(hook(facts.category()) + " | " + facts.title());
        if (facts.description() != null && !facts.description().isBlank()) {
            lines.add("");
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
        if (facts.applyMethod() != null || facts.deadlineAt() != null || facts.contactPhone() != null) {
            lines.add("");
        }
        if (facts.applyMethod() != null) {
            lines.add("신청: " + facts.applyMethod());
        }
        if (facts.deadlineAt() != null) {
            lines.add("신청 마감: %s(%s)까지".formatted(monthDay(facts.deadlineAt()), dayShort(facts.deadlineAt())));
        }
        if (facts.contactPhone() != null) {
            lines.add("문의: " + facts.contactPhone());
        }
        if (facts.hostName() != null) {
            lines.add("주최: " + facts.hostName());
        }
        return String.join("\n", lines);
    }

    // 분야별 한 줄 소개. 포스터 행사 이름 위에도 쓴다.
    static String hook(String category) {
        return category == null ? "함께라서 더 즐거운 하루" : HOOKS.getOrDefault(category, "함께라서 더 즐거운 하루");
    }

    String easy(Facts facts) {
        List<String> lines = new ArrayList<>();
        lines.add("%s에 같이 가요!".formatted(facts.title()));
        lines.add("");
        if (facts.startAt() != null) {
            lines.add("언제: " + easyWhen(facts));
        }
        if (facts.venueName() != null) {
            lines.add("어디: " + facts.venueName());
        }
        Set<String> sentences = new LinkedHashSet<>();
        for (int i = 0; i < facts.supportCodes().size(); i++) {
            String label = facts.supports().get(i);
            // "차량 승하차 지원"처럼 이미 '지원'으로 끝나면 한 번만 붙인다
            String fallback = label.endsWith("지원") ? label + "이 있어요." : label + " 지원이 있어요.";
            sentences.add(EASY_SENTENCES.getOrDefault(facts.supportCodes().get(i), fallback));
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
    static String generalWhen(Facts facts) {
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
