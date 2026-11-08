package dev.hidly.nubi.application.data.dto.response;

import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.enums.PrepStatus;
import dev.hidly.nubi.support.data.enums.SupportCategory;

import java.util.List;

// 필요 지원 모아보기(API-48, 화면 14). 거절·취소를 뺀 신청을 합친다.
public record EventNeedsResponseDto(
        Integer capacity,
        long participantCount,
        long companionCount,
        long newApplicationCount,
        int readyItemCount,
        List<NeedItem> items,
        List<CareNeed> cares,
        List<Arrival> arrivals
) {
    // 행사 날 준비할 것: 지원 항목별 합계. quantity가 비어 있는 신청은 그 신청의 이용자 수 전체로 센다.
    // prepStatus는 모든 신청이 READY면 READY, 하나라도 CHECK면 CHECK, 아니면 PENDING
    public record NeedItem(Long supportTagId, SupportCategory category, String code, String label, int totalQuantity,
                           PrepStatus prepStatus, List<NeedBreakdown> breakdown, List<String> hostNotes) {
    }

    public record NeedBreakdown(Long applicationId, Long applicationSupportId, String organizationName, String groupName,
                                int quantity, PrepStatus prepStatus) {
    }

    public record CareNeed(Long applicationId, String alias, String organizationName, String content) {
    }

    // 도착 순서: ERD에 도착 시각이 없어 신청 순서대로 오는 방법과 인원만 보여 준다.
    public record Arrival(Long applicationId, String organizationName, String groupName, String transport,
                          int participantCount, int companionCount, ApplicationStatus status) {
    }
}
