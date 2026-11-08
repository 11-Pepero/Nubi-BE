package dev.hidly.nubi.program.data.dto.request;

import dev.hidly.nubi.program.data.enums.ConditionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventConditionRequestDto {
    @NotNull
    private Long supportTagId;

    // 비우면 PROVIDED(주최가 제공한다고 체크한 항목)
    private ConditionStatus status;

    @Size(max = 500)
    private String evidence;

    public ConditionStatus statusOrDefault() {
        return status == null ? ConditionStatus.PROVIDED : status;
    }
}
