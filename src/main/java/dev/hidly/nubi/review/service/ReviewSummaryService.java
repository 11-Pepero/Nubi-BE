package dev.hidly.nubi.review.service;

import dev.hidly.nubi.common.ai.OpenAiClient;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ConditionSource;
import dev.hidly.nubi.program.data.enums.ConditionStatus;
import dev.hidly.nubi.program.data.exception.ProgramAccessDeniedException;
import dev.hidly.nubi.program.data.exception.ProgramNotFoundException;
import dev.hidly.nubi.program.data.repository.ProgramConditionRepository;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.review.data.dto.response.ReviewSummaryResponseDto;
import dev.hidly.nubi.review.data.dto.response.ReviewSummaryResponseDto.ConditionAlert;
import dev.hidly.nubi.review.data.entity.Review;
import dev.hidly.nubi.review.data.entity.ReviewCheck;
import dev.hidly.nubi.review.data.enums.CheckAnswer;
import dev.hidly.nubi.review.data.enums.ReviewReaction;
import dev.hidly.nubi.review.data.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// 주최 기관이 받은 후기를 읽고 다음 행사 개선점을 모은다(화면 16).
// 개선점·요약은 AI가 후기 글을 읽고 쓰고, 공고 조건과 후기가 엇갈린 항목(conditionAlerts)은 집계로 찾는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSummaryService {
    static final int MAX_IMPROVEMENTS = 5;
    static final int MAX_IMPROVEMENT_LENGTH = 40;
    static final int MAX_SUMMARY_LENGTH = 200;

    private static final Map<ReviewReaction, String> REACTION_LABELS = Map.of(
            ReviewReaction.ENJOYED, "즐거워했어요",
            ReviewReaction.WANT_AGAIN, "다시 가고 싶어요",
            ReviewReaction.HAD_DIFFICULTY, "조금 힘들어했어요",
            ReviewReaction.SHORT_BREAKS, "쉬는 시간이 부족했어요",
            ReviewReaction.LONG_DISTANCE, "이동 거리가 길었어요");
    // AI 없이 쓸 때 반응으로 만드는 개선점
    private static final Map<ReviewReaction, String> REACTION_IMPROVEMENTS = Map.of(
            ReviewReaction.SHORT_BREAKS, "쉬는 시간 늘리기",
            ReviewReaction.LONG_DISTANCE, "이동 거리 줄이기",
            ReviewReaction.HAD_DIFFICULTY, "어려웠던 순서 다시 살펴보기");

    static final String SYSTEM_PROMPT = """
            너는 장애인복지시설이 연 행사의 참여 기관 후기를 읽고, 주최 기관에 다음 행사 개선점을 정리해 주는 도우미다.
            입력으로 행사 이름과 후기 목록(별점, 내용, 달랐던 점, 반응, 지원 항목별 확인 결과)이 JSON으로 온다. 규칙:
            1. 후기에 실제로 나온 내용만 근거로 쓴다. 후기에 없는 문제나 칭찬을 만들지 않는다.
            2. improvements: 주최가 바로 할 수 있는 개선점을 중요한 순서로 최대 5개. 각 항목은 "그늘막 늘리기"처럼 20자 안팎의 짧은 명사형으로 쓴다.
               여러 후기에서 반복된 내용, "아니었어요" 확인 결과, 달랐던 점을 먼저 다룬다. 고칠 점이 없으면 빈 배열로 둔다.
            3. summary: 후기 전체 분위기를 한두 문장(200자 이내)으로. 기관 이름이나 사람 이름은 쓰지 않는다.
            4. 응답은 JSON 객체 하나로만 한다: {"summary": "...", "improvements": ["...", "..."]}
            """;

    private final ProgramRepository programRepository;
    private final ProgramConditionRepository programConditionRepository;
    private final ReviewRepository reviewRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    private record CheckCount(Long supportTagId, String code, String label, long yes, long no) {
    }

    @Transactional(readOnly = true)
    public GlobalResponse<ReviewSummaryResponseDto> summarize(Long eventId) {
        Program program = programRepository.findById(eventId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(ProgramNotFoundException::new);
        if (!program.isHostedBy(SecurityUtil.getOrganizationId())) {
            throw new ProgramAccessDeniedException();
        }

        List<Review> reviews = reviewRepository.findAllByProgramIdOrderByCreatedAtDesc(eventId);
        Map<Long, CheckCount> counts = countChecks(reviews);
        List<ConditionAlert> alerts = conditionAlerts(eventId, counts);
        if (reviews.isEmpty()) {
            return GlobalResponse.ok(new ReviewSummaryResponseDto(eventId, 0, false, "아직 받은 후기가 없어요.", List.of(), alerts));
        }

        String draftSummary = draftSummary(reviews);
        List<String> draftImprovements = draftImprovements(reviews, counts);
        Optional<JsonNode> response = openAiClient.isEnabled()
                ? openAiClient.completeJson(SYSTEM_PROMPT, objectMapper.writeValueAsString(aiInput(program, reviews)))
                : Optional.empty();

        String summary = draftSummary;
        List<String> improvements = draftImprovements;
        boolean aiWritten = false;
        if (response.isPresent()) {
            String writtenSummary = response.get().path("summary").asString("").trim();
            if (!writtenSummary.isBlank() && writtenSummary.length() <= MAX_SUMMARY_LENGTH) {
                summary = writtenSummary;
                aiWritten = true;
            }
            List<String> writtenImprovements = usableImprovements(response.get().path("improvements"));
            if (writtenImprovements != null) {
                improvements = writtenImprovements;
                aiWritten = true;
            }
        }
        log.info("[summarizeReviews] 받은 후기 요약: eventId={}, 후기 {}개, 개선점 {}개, 조건 엇갈림 {}개, AI {}",
                eventId, reviews.size(), improvements.size(), alerts.size(), aiWritten);

        return GlobalResponse.ok(new ReviewSummaryResponseDto(eventId, reviews.size(), aiWritten, summary, improvements, alerts));
    }

    // 배열이 아니거나, 너무 많거나, 너무 긴 항목이 있으면 쓰지 않는다(null).
    static List<String> usableImprovements(JsonNode node) {
        if (node == null || !node.isArray() || node.size() > MAX_IMPROVEMENTS) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : node) {
            String text = item.asString("").trim();
            if (text.isBlank() || text.length() > MAX_IMPROVEMENT_LENGTH) {
                return null;
            }
            items.add(text);
        }
        return items;
    }

    private Map<Long, CheckCount> countChecks(List<Review> reviews) {
        Map<Long, CheckCount> counts = new LinkedHashMap<>();
        for (Review review : reviews) {
            for (ReviewCheck check : review.getChecks()) {
                Long tagId = check.getSupportTag().getId();
                CheckCount before = counts.getOrDefault(tagId, new CheckCount(tagId, check.getSupportTag().getCode(),
                        check.getSupportTag().getLabel(), 0, 0));
                counts.put(tagId, new CheckCount(tagId, before.code(), before.label(),
                        before.yes() + (check.getAnswer() == CheckAnswer.YES ? 1 : 0),
                        before.no() + (check.getAnswer() == CheckAnswer.NO ? 1 : 0)));
            }
        }
        return counts;
    }

    // 주최가 제공한다고 체크(HOST, PROVIDED)했는데 후기에서 "아니었어요"가 나온 항목
    private List<ConditionAlert> conditionAlerts(Long eventId, Map<Long, CheckCount> counts) {
        Set<Long> promised = programConditionRepository.findAllByProgramIdOrderByIdAsc(eventId).stream()
                .filter(condition -> condition.getSource() == ConditionSource.HOST
                        && condition.getStatus() == ConditionStatus.PROVIDED)
                .map(condition -> condition.getSupportTag().getId())
                .collect(Collectors.toSet());
        return counts.values().stream()
                .filter(count -> promised.contains(count.supportTagId()) && count.no() > 0)
                .map(count -> new ConditionAlert(count.supportTagId(), count.code(), count.label(), count.yes(), count.no(),
                        "공고에는 제공한다고 했지만 후기 %d건이 '아니었어요'라고 했어요. 다음 행사 전에 확인해 주세요."
                                .formatted(count.no())))
                .toList();
    }

    private static String draftSummary(List<Review> reviews) {
        double average = reviews.stream().mapToInt(Review::getRating).average().orElse(0);
        return "후기 %d개, 평균 별점 %.1f점이에요.".formatted(reviews.size(), average);
    }

    private static List<String> draftImprovements(List<Review> reviews, Map<Long, CheckCount> counts) {
        Set<String> items = new LinkedHashSet<>();
        counts.values().stream()
                .filter(count -> count.no() > 0)
                .sorted((a, b) -> Long.compare(b.no(), a.no()))
                .forEach(count -> items.add(count.label() + " 다시 확인하기"));
        Map<ReviewReaction, Long> reactions = reviews.stream()
                .flatMap(review -> review.getReactions().stream())
                .collect(Collectors.groupingBy(reaction -> reaction, Collectors.counting()));
        reactions.entrySet().stream()
                .filter(entry -> REACTION_IMPROVEMENTS.containsKey(entry.getKey()))
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(entry -> items.add(REACTION_IMPROVEMENTS.get(entry.getKey())));
        return items.stream().limit(MAX_IMPROVEMENTS).toList();
    }

    // AI에는 기관 이름 없이 후기 내용만 넘긴다.
    private static Map<String, Object> aiInput(Program program, List<Review> reviews) {
        List<Map<String, Object>> items = reviews.stream().map(review -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rating", review.getRating());
            item.put("content", review.getContent());
            item.put("mismatchNote", review.getMismatchNote());
            item.put("reactions", review.getReactions().stream().map(REACTION_LABELS::get).toList());
            item.put("checks", review.getChecks().stream()
                    .map(check -> Map.of("item", check.getSupportTag().getLabel(), "answer", switch (check.getAnswer()) {
                        case YES -> "그랬어요";
                        case NO -> "아니었어요";
                        case UNKNOWN -> "몰라요";
                    }))
                    .toList());
            return item;
        }).toList();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("eventTitle", program.getTitle());
        input.put("reviews", items);
        return input;
    }
}
