package dev.hidly.nubi.application.data.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectApplicationRequestDto {
    @NotNull
    private Long groupId;

    @Size(max = 100)
    private List<@NotNull Long> careIds;

    // 비우면 그룹 인원
    @Min(1)
    @Max(1000)
    private Integer participantCount;

    @Min(0)
    @Max(1000)
    private Integer companionCount;

    @Size(max = 50)
    private String transport;

    @Size(max = 1000)
    private String message;

    public List<Long> careIdsOrEmpty() {
        return careIds == null ? List.of() : careIds;
    }
}
