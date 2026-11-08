package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.program.data.entity.EligibilityReasons;
import dev.hidly.nubi.program.data.enums.EligibilityResult;

public record EligibilityResponseDto(Long programId, Long groupId, EligibilityResult result, EligibilityReasons reasons) {
}
