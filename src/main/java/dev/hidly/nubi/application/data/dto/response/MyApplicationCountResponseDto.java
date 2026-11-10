package dev.hidly.nubi.application.data.dto.response;

import dev.hidly.nubi.application.data.enums.ApplicationStatus;

import java.util.Map;

// 내 신청 요약(API-30, 화면 05 위쪽 카드와 탭)
// preparing = PREPARING, waiting = SUBMITTED + INQUIRY + WAITING, upcoming = CONFIRMED, attended = ATTENDED
// total은 네 묶음의 합(거절·취소 제외), upcomingThisWeek는 이번 주(월~일) 안에 시작하는 참여 예정
// reviewPending = "후기 n건 남기기". 후기를 쓸 수 있는데(확정·다녀옴이고 행사가 시작됨) 아직 안 쓴 신청 수
public record MyApplicationCountResponseDto(
        long total,
        long preparing,
        long waiting,
        long upcoming,
        long upcomingThisWeek,
        long attended,
        long reviewPending,
        Map<ApplicationStatus, Long> statusCounts
) {
}
