package dev.hidly.nubi.support.data.dto.response;

import dev.hidly.nubi.support.data.entity.SupportTag;

public record SupportTagResponseDto(Long id, String code, String label) {
    public static SupportTagResponseDto from(SupportTag tag) {
        return new SupportTagResponseDto(tag.getId(), tag.getCode(), tag.getLabel());
    }
}
