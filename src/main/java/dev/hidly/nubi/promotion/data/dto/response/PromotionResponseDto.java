package dev.hidly.nubi.promotion.data.dto.response;

// 행사 올리기(화면 06) AI 홍보문 탭: 일반 / 쉬운 말 / 문자용
public record PromotionResponseDto(
        Long eventId,
        // true면 AI가 다듬은 글이 하나 이상, false면 AI 없이 쓴 초안(키가 없거나 AI 호출이 실패한 경우)
        boolean aiWritten,
        String general,
        String easy,
        String sms
) {
}
