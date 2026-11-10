package dev.hidly.nubi.review.data.dto.response;

import java.util.List;

// 받은 후기(화면 16)의 "AI가 모은 다음 행사 개선점"
public record ReviewSummaryResponseDto(
        Long eventId,
        long reviewCount,
        // true면 AI가 후기를 읽고 쓴 요약, false면 AI 없이 집계로 만든 요약(후기가 없거나 AI가 실패한 경우)
        boolean aiWritten,
        String summary,
        List<String> improvements,
        // 주최가 "제공한다"고 체크했는데 후기에서 "아니었어요"가 나온 지원 항목. AI가 아니라 집계로 찾는다.
        List<ConditionAlert> conditionAlerts
) {
    public record ConditionAlert(Long supportTagId, String code, String label, long yesCount, long noCount, String message) {
    }
}
