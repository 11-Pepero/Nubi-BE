package dev.hidly.nubi.program.data.dto.response;

// 행사 관리 요약(API-40, 화면 11 위쪽 카드와 탭 숫자)
// ongoingEventCount = 공개 중(OPEN) + 마감(CLOSED), pastEventCount = 끝난 행사(ENDED), draftEventCount = 임시 저장(DRAFT)
// newApplicationCount = 주최가 아직 정하지 않은 신청(SUBMITTED)
// upcomingParticipantCount, upcomingCompanionCount = 이번 달에 시작하는 행사의 확정(CONFIRMED) 신청 인원
// reviewCount = 내가 주최한 행사(삭제 제외)에 달린 받은 후기 수(공개·비공개 모두)
public record EventDashboardResponseDto(
        long ongoingEventCount,
        long pastEventCount,
        long draftEventCount,
        long newApplicationCount,
        long upcomingParticipantCount,
        long upcomingCompanionCount,
        long reviewCount
) {
}
