package dev.hidly.nubi.program.data.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProgramImageOrderRequestDto(@NotEmpty List<Long> imageIds) {
}
