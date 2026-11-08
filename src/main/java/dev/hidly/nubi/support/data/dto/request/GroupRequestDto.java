package dev.hidly.nubi.support.data.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class GroupRequestDto {
    @NotBlank
    @Size(max = 50)
    private String name;

    @NotNull
    @Min(1)
    @Max(1000)
    private Integer memberCount;

    @Valid
    private List<@NotNull @Valid GroupSupportRequestDto> supports;

    public List<GroupSupportRequestDto> supportsOrEmpty() {
        return supports == null ? List.of() : supports;
    }
}
