package dev.hidly.nubi.support.data.dto.response;

import dev.hidly.nubi.support.data.enums.SupportCategory;

public record SupportCountResponseDto(Long supportTagId, SupportCategory category, String code, String label, int count) {
}
