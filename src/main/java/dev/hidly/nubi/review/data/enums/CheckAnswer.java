package dev.hidly.nubi.review.data.enums;

import dev.hidly.nubi.program.data.enums.ConditionStatus;

// 공고에 적힌 대로였나요? 그랬어요 / 아니었어요 / 몰라요
public enum CheckAnswer {
    YES,
    NO,
    UNKNOWN;

    // 프로그램 조건에 남길 값. 몰라요는 조건을 바꾸지 않는다.
    public ConditionStatus toConditionStatus() {
        return switch (this) {
            case YES -> ConditionStatus.PROVIDED;
            case NO -> ConditionStatus.NOT_PROVIDED;
            case UNKNOWN -> null;
        };
    }
}
