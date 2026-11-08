package dev.hidly.nubi.program.data.dto.request;

import dev.hidly.nubi.program.data.enums.ProgramCategory;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
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

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCreateRequestDto {
    @NotBlank
    @Size(max = 150)
    private String title;

    private ProgramCategory category;

    @Size(max = 5000)
    private String description;

    private Long venueId;

    @NotNull
    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private LocalDateTime deadlineAt;

    @Min(1)
    @Max(10000)
    private Integer capacity;

    @Size(max = 20)
    private String contactPhone;

    // DRAFT(임시 저장) 또는 OPEN(공개하기)
    @NotNull
    private ProgramStatus status;

    @Valid
    private List<EventConditionRequestDto> conditions;

    public List<EventConditionRequestDto> conditionsOrEmpty() {
        return conditions == null ? List.of() : conditions;
    }
}
