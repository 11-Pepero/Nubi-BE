package dev.hidly.nubi.application.data.dto.response;

import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.common.response.PageResponse;

import java.util.Map;

// 신청 기관 목록(API-43): 위쪽 숫자 카드, 탭별 건수, 표
// participantCount·companionCount·newApplicationCount는 거절·취소를 뺀 신청 기준.
// 정원(capacity)은 이용자 수로만 센다. 화면에 "이용자 + 동반"으로 보여 줄지는 프론트가 정한다.
public record EventApplicationListResponseDto(
        Integer capacity,
        long applicantOrgCount,
        long participantCount,
        long companionCount,
        long newApplicationCount,
        Map<ApplicationStatus, Long> statusCounts,
        PageResponse<EventApplicationSummaryResponseDto> applications
) {
}
