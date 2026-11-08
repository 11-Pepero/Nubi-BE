package dev.hidly.nubi.support.data.dto.response;

import dev.hidly.nubi.program.service.EligibilityService.ResultCount;
import dev.hidly.nubi.support.data.entity.SupportGroup;

import java.time.LocalDateTime;
import java.util.List;

public record GroupResponseDto(
        Long id,
        String name,
        int memberCount,
        long careCount,
        long eligibleCount,
        long checkCount,
        List<GroupSupportResponseDto> supports,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GroupResponseDto of(SupportGroup group, long careCount, ResultCount resultCount) {
        return new GroupResponseDto(
                group.getId(),
                group.getName(),
                group.getMemberCount(),
                careCount,
                resultCount.eligible(),
                resultCount.check(),
                group.getSupports().stream().map(GroupSupportResponseDto::from).toList(),
                group.getCreatedAt(),
                group.getUpdatedAt());
    }
}
