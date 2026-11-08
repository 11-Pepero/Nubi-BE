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
public class PrepareApplicationRequestDto {
    @NotNull
    private Long programId;

    @NotNull
    private Long groupId;

    @Size(max = 100)
    private List<@NotNull Long> careIds;

    // 명세에는 없지만 화면 08의 "8명 + 동반 4명"을 위해 바로 신청과 같게 받는다. 비우면 그룹 인원, 동반 0명.
    @Min(1)
    @Max(1000)
    private Integer participantCount;

    @Min(0)
    @Max(1000)
    private Integer companionCount;

    public List<Long> careIdsOrEmpty() {
        return careIds == null ? List.of() : careIds;
    }
}
