package dev.hidly.nubi.application.data.enums;

import java.util.Set;

public enum ApplicationStatus {
    PREPARING,
    SUBMITTED,
    INQUIRY,
    WAITING,
    CONFIRMED,
    REJECTED,
    ATTENDED,
    CANCELLED;

    // 같은 그룹이 같은 행사에 다시 신청하지 못하게 막는 "진행 중" 상태
    public static final Set<ApplicationStatus> IN_PROGRESS = Set.of(PREPARING, SUBMITTED, INQUIRY, WAITING, CONFIRMED);

    // 정원을 차지하지 않는 상태
    public static final Set<ApplicationStatus> RELEASED = Set.of(REJECTED, CANCELLED);

    // 주최 기관이 확정·대기·거절을 정할 수 있는 지금 상태와 바꿀 수 있는 상태 (API-45)
    public static final Set<ApplicationStatus> HOST_DECIDABLE_FROM = Set.of(SUBMITTED, INQUIRY, WAITING, CONFIRMED);
    public static final Set<ApplicationStatus> HOST_DECISIONS = Set.of(CONFIRMED, WAITING, REJECTED);

    public boolean canBeDecidedByHostAs(ApplicationStatus next) {
        return this != next && HOST_DECIDABLE_FROM.contains(this) && HOST_DECISIONS.contains(next);
    }
}
