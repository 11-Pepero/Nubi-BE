package dev.hidly.nubi.support.data.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class CarePersonCreateRequestDto implements CarePersonRequest {
    @NotBlank
    @Size(max = 20)
    private String alias;

    private Long groupId;

    @Valid
    @Size(max = 30)
    private List<CareItemRequestDto> items;

    @Size(max = 1000)
    private String memo;

    private Boolean applyToJudgement;
}
