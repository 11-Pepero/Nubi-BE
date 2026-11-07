package dev.hidly.nubi.support.data.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SupportCategory {
    MOBILITY("이동"),
    TOILET("화장실"),
    COMPANION("동반"),
    COMMUNICATION("소통"),
    ENVIRONMENT("환경"),
    MEAL("식사"),
    REST("휴식"),
    TIME("시간"),
    ETC("기타");

    private final String label;
}
