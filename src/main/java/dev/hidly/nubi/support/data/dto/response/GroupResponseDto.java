package dev.hidly.nubi.support.data.dto.response;

import dev.hidly.nubi.support.data.entity.SupportGroup;

import java.time.LocalDateTime;
import java.util.List;

public record GroupResponseDto(
        Long id,
        String name,
        int memberCount,
        List<GroupSupportResponseDto> supports,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GroupResponseDto from(SupportGroup group) {
        return new GroupResponseDto(
                group.getId(),
                group.getName(),
                group.getMemberCount(),
                group.getSupports().stream().map(GroupSupportResponseDto::from).toList(),
                group.getCreatedAt(),
                group.getUpdatedAt());
    }
}
