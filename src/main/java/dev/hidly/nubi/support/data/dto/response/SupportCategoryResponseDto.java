package dev.hidly.nubi.support.data.dto.response;

import dev.hidly.nubi.support.data.enums.SupportCategory;

import java.util.List;

public record SupportCategoryResponseDto(SupportCategory category, String label, List<SupportTagResponseDto> tags) {
}
