package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.program.data.entity.EligibilityReasons;
import dev.hidly.nubi.program.data.enums.EligibilityResult;

import java.time.LocalDateTime;
import java.util.List;

public record JudgementImpactResponseDto(boolean applied, long affectedCount, List<AffectedProgram> programs) {
    public static final JudgementImpactResponseDto NOT_APPLIED = new JudgementImpactResponseDto(false, 0, List.of());

    public record AffectedProgram(
            Long programId,
            String title,
            LocalDateTime startAt,
            EligibilityResult resultWithout,
            EligibilityResult resultWith,
            List<EligibilityReasons.CareCheck> checks
    ) {
    }
}
