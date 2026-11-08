package dev.hidly.nubi.support.data.dto.response;

import dev.hidly.nubi.program.data.dto.response.JudgementImpactResponseDto;
import dev.hidly.nubi.support.data.entity.CarePerson;
import dev.hidly.nubi.support.data.entity.SupportGroup;

import java.time.LocalDateTime;
import java.util.List;

public record CarePersonResponseDto(
        Long id,
        String alias,
        Long groupId,
        String groupName,
        List<CareItemResponseDto> items,
        String memo,
        boolean applyToJudgement,
        JudgementImpactResponseDto judgementImpact,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CarePersonResponseDto from(CarePerson carePerson) {
        return of(carePerson, null);
    }

    public static CarePersonResponseDto of(CarePerson carePerson, JudgementImpactResponseDto judgementImpact) {
        SupportGroup group = carePerson.getGroup();
        return new CarePersonResponseDto(
                carePerson.getId(),
                carePerson.getAlias(),
                group == null ? null : group.getId(),
                group == null ? null : group.getName(),
                carePerson.getItems().stream().map(CareItemResponseDto::from).toList(),
                carePerson.getMemo(),
                carePerson.isApplyToJudgement(),
                judgementImpact,
                carePerson.getCreatedAt(),
                carePerson.getUpdatedAt());
    }
}
