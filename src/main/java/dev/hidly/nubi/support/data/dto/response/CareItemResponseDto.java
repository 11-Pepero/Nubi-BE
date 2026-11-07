package dev.hidly.nubi.support.data.dto.response;

import dev.hidly.nubi.support.data.entity.CareItem;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.enums.SupportCategory;

public record CareItemResponseDto(
        Long id,
        Long supportTagId,
        SupportCategory category,
        String code,
        String label,
        String content
) {
    public static CareItemResponseDto from(CareItem item) {
        SupportTag tag = item.getSupportTag();
        return new CareItemResponseDto(
                item.getId(),
                tag == null ? null : tag.getId(),
                tag == null ? null : tag.getCategory(),
                tag == null ? null : tag.getCode(),
                tag == null ? null : tag.getLabel(),
                item.getContent());
    }
}
