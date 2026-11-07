package dev.hidly.nubi.support.data.dto.response;

import dev.hidly.nubi.support.data.entity.SupportGroup;

import java.time.LocalDateTime;
import java.util.List;

public record GroupResponseDto(
        Long id,
        String name,
        int memberCount,
        long careCount,
        List<GroupSupportResponseDto> supports,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GroupResponseDto of(SupportGroup group, long careCount) {
        return new GroupResponseDto(
                group.getId(),
                group.getName(),
                group.getMemberCount(),
                careCount,
                group.getSupports().stream().map(GroupSupportResponseDto::from).toList(),
                group.getCreatedAt(),
                group.getUpdatedAt());
    }
}
