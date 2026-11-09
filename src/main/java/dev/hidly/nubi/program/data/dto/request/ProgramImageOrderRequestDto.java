package dev.hidly.nubi.program.data.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProgramImageOrderRequestDto(@NotEmpty List<@NotNull Long> imageIds) {
}
