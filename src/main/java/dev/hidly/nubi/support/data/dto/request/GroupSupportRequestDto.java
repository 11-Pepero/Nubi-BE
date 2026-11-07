package dev.hidly.nubi.support.data.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSupportRequestDto {
    @NotNull
    private Long supportTagId;

    @Min(1)
    private Integer quantity;

    @Size(max = 10)
    @Pattern(regexp = "^[1-9]\\d*:[1-9]\\d*$")
    private String ratio;

    @Size(max = 200)
    private String note;
}
