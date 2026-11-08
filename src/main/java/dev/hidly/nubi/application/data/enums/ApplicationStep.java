package dev.hidly.nubi.application.data.enums;

// 신청 현황(화면 05)의 단계 표시: 준비 → 접수 → 참여 → 후기. 거절·취소는 ENDED.
public enum ApplicationStep {
    PREPARE,
    SUBMIT,
    ATTEND,
    REVIEW,
    ENDED;

    public static ApplicationStep of(ApplicationStatus status) {
        return switch (status) {
            case PREPARING -> PREPARE;
            case SUBMITTED, INQUIRY, WAITING -> SUBMIT;
            case CONFIRMED -> ATTEND;
            case ATTENDED -> REVIEW;
            case REJECTED, CANCELLED -> ENDED;
        };
    }
}
