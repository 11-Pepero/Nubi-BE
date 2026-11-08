package dev.hidly.nubi.application.data.repository;

// 행사 하나에 들어온 신청 숫자 (거절·취소 제외). 새 신청은 아직 주최가 정하지 않은 SUBMITTED.
public record EventApplicationStats(
        Long programId,
        Long applicantOrgCount,
        Long participantCount,
        Long companionCount,
        Long newApplicationCount
) {
    public static EventApplicationStats empty(Long programId) {
        return new EventApplicationStats(programId, 0L, 0L, 0L, 0L);
    }
}
