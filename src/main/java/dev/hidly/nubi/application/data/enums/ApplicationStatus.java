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
}
