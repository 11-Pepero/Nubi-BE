package dev.hidly.nubi.organization.data.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum District {
    CHEOIN("처인구"),
    GIHEUNG("기흥구"),
    SUJI("수지구");

    private final String label;

    public static Optional<District> fromAddress(String address) {
        if (address == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(district -> address.contains(district.label))
                .findFirst();
    }
}
