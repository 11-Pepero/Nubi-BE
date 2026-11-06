package dev.hidly.nubi.organization.data.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum District {
    CHEOIN("처인구"),
    GIHEUNG("기흥구"),
    SUJI("수지구");

    private final String label;
}
