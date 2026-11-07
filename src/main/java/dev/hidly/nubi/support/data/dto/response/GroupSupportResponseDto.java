package dev.hidly.nubi.support.data.dto.response;

import dev.hidly.nubi.support.data.entity.GroupSupport;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.enums.SupportCategory;

public record GroupSupportResponseDto(
        Long supportTagId,
        SupportCategory category,
        String code,
        String label,
        Integer quantity,
        String ratio,
        String note
) {
    public static GroupSupportResponseDto from(GroupSupport support) {
        SupportTag tag = support.getSupportTag();
        return new GroupSupportResponseDto(
                tag.getId(),
                tag.getCategory(),
                tag.getCode(),
                tag.getLabel(),
                support.getQuantity(),
                support.getRatio(),
                support.getNote());
    }
}
